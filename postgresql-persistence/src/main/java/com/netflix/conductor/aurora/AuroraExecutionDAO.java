package com.netflix.conductor.aurora;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventPublished;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class AuroraExecutionDAO extends AuroraBaseDAO implements ExecutionDAO {
	private final MetadataDAO metadata;
	private final IndexDAO indexer;

	@Inject
	public AuroraExecutionDAO(DataSource dataSource, ObjectMapper mapper, MetadataDAO metadata, IndexDAO indexer) {
		super(dataSource, mapper);
		this.metadata = metadata;
		this.indexer = indexer;
	}

	@Override
	@Deprecated // Not used by core engine
	public List<Task> getPendingTasksByWorkflow(String taskName, String workflowId) {
		String SQL = "SELECT t.json_data FROM task_in_progress tip " +
			"INNER JOIN task t ON t.task_id = tip.task_id " +
			"WHERE tip.task_def_name = ? AND tip.workflow_id = ?";

		return queryWithTransaction(SQL, q -> q.addParameter(taskName)
			.addParameter(workflowId)
			.executeAndFetch(Task.class));
	}

	@Override
	public List<Task> getPendingTasksForTaskType(String taskType) {
		String SQL = "SELECT t.json_data FROM task_in_progress tip " +
			"INNER JOIN task t ON t.task_id = tip.task_id " +
			"WHERE tip.task_def_name = ?";

		return queryWithTransaction(SQL,
			q -> q.addParameter(taskType).executeAndFetch(Task.class));
	}

	@Override
	public List<Task> getPendingSystemTasks(String taskType) {
		String SQL = "SELECT json_data FROM task WHERE task_type = ? AND task_status = ?";

		return queryWithTransaction(SQL,
			q -> q.addParameter(taskType)
				.addParameter("IN_PROGRESS")
				.executeAndFetch(Task.class));
	}

	@Override
	public List<Task> getTasks(String taskType, String startKey, int count) {
		List<Task> tasks = Lists.newLinkedList();

		List<Task> pendingTasks = getPendingTasksForTaskType(taskType);
		boolean startKeyFound = startKey == null;
		int foundCount = 0;
		for (Task pendingTask : pendingTasks) {
			if (!startKeyFound) {
				if (pendingTask.getTaskId().equals(startKey)) {
					startKeyFound = true;
					continue;
				}
			}
			if (startKeyFound && foundCount < count) {
				tasks.add(pendingTask);
				foundCount++;
			}
		}
		return tasks;
	}

	@Override
	public List<Task> createTasks(List<Task> tasks) {
		List<Task> created = Lists.newLinkedList();

		withTransaction(tx -> {
			for (Task task : tasks) {
				Preconditions.checkNotNull(task, "task object cannot be null");
				Preconditions.checkNotNull(task.getTaskId(), "Task id cannot be null");
				Preconditions.checkNotNull(task.getWorkflowInstanceId(), "Workflow instance id cannot be null");
				Preconditions.checkNotNull(task.getReferenceTaskName(), "Task reference name cannot be null");

				boolean taskAdded = addScheduledTask(tx, task);
				if (!taskAdded) {
					String taskKey = task.getReferenceTaskName() + task.getRetryCount();
					logger.debug("Task already scheduled, skipping the run " + task.getTaskId() +
						", ref=" + task.getReferenceTaskName() + ", key=" + taskKey);
					continue;
				}
				// Set schedule time here, after task was added to schedule table (it does not contain schedule time)
				task.setScheduledTime(System.currentTimeMillis());
				//The flag is boolean object, setting it to false so workflow executor can properly determine the state
				task.setStarted(false);
				addTaskInProgress(tx, task);
				insertOrUpdateTask(tx, task, false);

				created.add(task);
			}
		});

		return created;
	}

	@Override
	public void updateTask(Task task) {
		withTransaction(tx -> insertOrUpdateTask(tx, task, true));
	}

	@Override
	public boolean exceedsInProgressLimit(Task task) {
		TaskDef taskDef = metadata.getTaskDef(task.getTaskDefName());
		if (taskDef == null) {
			return false;
		}

		int limit = taskDef.concurrencyLimit();
		if (limit <= 0) {
			return false;
		}

		long current = getInProgressTaskCount(task.getTaskDefName());
		if (current >= limit) {
			return true;
		}

		logger.debug("Task execution count for {}: limit={}, current={}", task.getTaskDefName(), limit, current);

		String SQL = "SELECT task_id FROM task_in_progress WHERE task_def_name = ? ORDER BY id LIMIT ?";
		List<String> taskIds = queryWithTransaction(SQL,
			q -> q.addParameter(task.getTaskDefName()).addParameter(limit).executeScalarList(String.class));

		boolean rateLimited = !taskIds.contains(task.getTaskId());
		if (rateLimited) {
			logger.debug("Task execution count limited. {}, limit {}, current {}", task.getTaskDefName(), limit, current);
		}

		return rateLimited;
	}

	@Override
	public boolean exceedsRateLimitPerFrequency(Task task) {
		TaskDef taskDef = metadata.getTaskDef(task.getTaskDefName());
		if (taskDef == null) {
			return false;
		}

		Integer rateLimitPerFrequency = taskDef.getRateLimitPerFrequency();
		Integer rateLimitFrequencyInSeconds = taskDef.getRateLimitFrequencyInSeconds();
		if (rateLimitPerFrequency == null || rateLimitPerFrequency <= 0 ||
			rateLimitFrequencyInSeconds == null || rateLimitFrequencyInSeconds <= 0) {
			return false;
		}

		logger.debug("Evaluating rate limiting for Task: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {}",
			task, rateLimitPerFrequency, rateLimitFrequencyInSeconds);

		long currentTimeEpochMillis = System.currentTimeMillis();
		long currentTimeEpochMinusRateLimitBucket = currentTimeEpochMillis - (rateLimitFrequencyInSeconds * 1000);

		// Delete the expired records first
		String SQL = "DELETE FROM task_rate_limit WHERE task_def_name = ? AND created_on < ?";
		executeWithTransaction(SQL, q -> q
			.addParameter(taskDef.getName())
			.addTimestampParameter(currentTimeEpochMinusRateLimitBucket)
			.executeDelete());

		// Count how many left between currentTimeEpochMinusRateLimitBucket and currentTimeEpochMillis
		SQL = "SELECT COUNT(*) FROM task_rate_limit WHERE task_def_name = ? AND created_on BETWEEN ? AND ?";
		long currentBucketCount = queryWithTransaction(SQL, q -> q
			.addParameter(taskDef.getName())
			.addTimestampParameter(currentTimeEpochMinusRateLimitBucket)
			.addTimestampParameter(currentTimeEpochMillis)
			.executeScalar(Long.class));

		// Within the rate limit
		if (currentBucketCount < rateLimitPerFrequency) {
			SQL = "INSERT INTO task_rate_limit(created_on,expires_on,task_def_name) VALUES (?,?,?)";
			executeWithTransaction(SQL, q -> q
				.addTimestampParameter(currentTimeEpochMillis)
				.addTimestampParameter(System.currentTimeMillis() + (rateLimitFrequencyInSeconds * 1000))
				.addParameter(taskDef.getName())
				.executeUpdate());

			logger.debug("Task: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} within the rate limit with current count {}",
				task, rateLimitPerFrequency, rateLimitFrequencyInSeconds, ++currentBucketCount);
			return false;
		}

		logger.debug("Task: {} with rateLimitPerFrequency: {} and rateLimitFrequencyInSeconds: {} is out of bounds of rate limit with current count {}",
			task, rateLimitPerFrequency, rateLimitFrequencyInSeconds, currentBucketCount);
		return true;
	}

	@Override
	public void updateTasks(List<Task> tasks) {
		withTransaction(tx -> {
			for (Task task : tasks) {
				insertOrUpdateTask(tx, task, true);
			}
		});
	}

	@Override
	public void addTaskExecLog(List<TaskExecLog> log) {
		indexer.add(log);
	}

	@Override
	public void removeTask(String taskId) {
		withTransaction(tx -> {
			Task task = getTask(tx, taskId);
			if (task == null) {
				logger.debug("No such task found by id {}", taskId);
				return;
			}
			removeTask(tx, task);
		});
	}

	@Override
	public void removeTask(Task task) {
		withTransaction(tx -> removeTask(tx, task));
	}

	@Override
	public Task getTask(String taskId) {
		return getWithTransaction(tx -> getTask(tx, taskId));
	}

	@Override
	public Task getTask(String workflowId, String taskRefName) {
		String GET_TASK = "SELECT json_data FROM task WHERE workflow_id = ? and task_refname = ? ORDER BY id DESC";
		return queryWithTransaction(GET_TASK, q -> q
			.addParameter(workflowId)
			.addParameter(taskRefName)
			.executeAndFetchFirst(Task.class));
	}

	@Override
	public List<Task> getTasks(List<String> taskIds) {
		if (taskIds == null || taskIds.isEmpty()) {
			return Lists.newArrayList();
		}

		String GET_TASKS = "SELECT json_data FROM task WHERE task_id = ANY(?)";
		return queryWithTransaction(GET_TASKS, q -> q
			.addParameter(taskIds)
			.executeAndFetch(Task.class));
	}

	@Override
	public List<Task> getTasksForWorkflow(String workflowId) {
		String SQL = "SELECT json_data FROM task WHERE workflow_id = ?";
		return queryWithTransaction(SQL, q -> q.addParameter(workflowId).executeAndFetch(Task.class));
	}

	@Override
	public String createWorkflow(Workflow workflow) {
		return insertOrUpdateWorkflow(workflow, false);
	}

	@Override
	public String updateWorkflow(Workflow workflow) {
		return insertOrUpdateWorkflow(workflow, true);
	}

	@Override
	public void removeWorkflow(String workflowId) {
		Workflow workflow = getWorkflow(workflowId, true);
		if (workflow == null)
			return;

		withTransaction(tx -> {
			for (Task task : workflow.getTasks()) {
				removeTask(tx, task);
			}
			removeWorkflow(tx, workflowId);
		});
	}

	@Override
	@Deprecated
	public void removeFromPendingWorkflow(String workflowType, String workflowId) {
		// not in use any more. See references
	}

	@Override
	public Workflow getWorkflow(String workflowId) {
		return getWorkflow(workflowId, true);
	}

	@Override
	public Workflow getWorkflow(String workflowId, boolean includeTasks) {
		Workflow workflow = getWithTransaction(tx -> readWorkflow(tx, workflowId));
		if (workflow != null && includeTasks) {
			List<Task> tasks = getTasksForWorkflow(workflowId);
			tasks.sort(Comparator.comparingLong(Task::getScheduledTime).thenComparingInt(Task::getSeq));
			workflow.setTasks(tasks);
		}
		return workflow;
	}

	@Override
	public List<String> getRunningWorkflowIds(String workflowName) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		return getWithTransaction(tx -> getRunningWorkflowIds(tx, workflowName));
	}

	@Override
	public List<Workflow> getPendingWorkflowsByType(String workflowName) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		List<String> workflowIds = getWithTransaction(tx -> getRunningWorkflowIds(tx, workflowName));
		return workflowIds.stream()
			.map(id -> getWorkflow(id, true))
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}

	@Override
	public long getPendingWorkflowCount(String workflowName) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		String SQL = "SELECT COUNT(*) FROM workflow WHERE workflow_type = ? AND workflow_status IN ('RUNNING','PAUSED')";
		return queryWithTransaction(SQL, q -> q.addParameter(workflowName).executeCount());
	}

	@Override
	public long getInProgressTaskCount(String taskDefName) {
		String SQL = "SELECT COUNT(*) FROM task_in_progress WHERE task_def_name = ? AND in_progress = true";
		return queryWithTransaction(SQL, q -> q.addParameter(taskDefName).executeCount());
	}

	@Override
	public List<Workflow> getWorkflowsByType(String workflowName, Long startTime, Long endTime) {
		Preconditions.checkNotNull(workflowName, "workflowName cannot be null");
		Preconditions.checkNotNull(startTime, "startTime cannot be null");
		Preconditions.checkNotNull(endTime, "endTime cannot be null");

		String SQL = "SELECT workflow_id FROM workflow WHERE workflow_type = ? AND date_str BETWEEN ? AND ?";
		List<String> workflowIds = queryWithTransaction(SQL, q -> q.addParameter(workflowName)
			.addParameter(dateStr(startTime))
			.addParameter(dateStr(endTime))
			.executeScalarList(String.class));

		List<Workflow> workflows = new LinkedList<>();
		workflowIds.forEach(workflowId -> {
			try {
				Workflow wf = getWorkflow(workflowId);
				if (wf.getCreateTime() >= startTime && wf.getCreateTime() <= endTime) {
					workflows.add(wf);
				}
			} catch (Exception e) {
				logger.error("Unable to load workflow id {} with name {}", workflowId, workflowName, e);
			}
		});
		return workflows;
	}

	@Override
	public List<Workflow> getWorkflowsByCorrelationId(String correlationId) {
		Preconditions.checkNotNull(correlationId, "correlationId cannot be null");
		String SQL = "SELECT workflow_id FROM workflow WHERE correlation_id = ?";
		List<String> workflowIds = queryWithTransaction(SQL, q -> q.addParameter(correlationId).executeScalarList(String.class));
		return workflowIds.stream().map(this::getWorkflow).collect(Collectors.toList());
	}

	@Override
	public boolean addEventExecution(EventExecution ee) {
		return getWithTransaction(tx -> insertEventExecution(tx, ee));
	}

	@Override
	public void updateEventExecution(EventExecution ee) {
		withTransaction(tx -> updateEventExecution(tx, ee));
	}

	@Override
	@Deprecated
	public List<EventExecution> getEventExecutions(String eventHandlerName, String eventName, String messageId, int max) {
		// not in use any more. See references
		return Collections.emptyList();
	}

	@Override
	public void addMessage(String queue, Message msg) {
		indexer.addMessage(queue, msg);
	}

	@Override
	public void updateLastPoll(String taskDefName, String domain, String workerId) {
		Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
		PollData pollData = new PollData(taskDefName, domain, workerId, System.currentTimeMillis());
		String effectiveDomain = (domain == null) ? "DEFAULT" : domain;
		withTransaction(tx -> insertOrUpdatePollData(tx, pollData, effectiveDomain));
	}

	@Override
	public PollData getPollData(String taskDefName, String domain) {
		Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
		String effectiveDomain = (domain == null) ? "DEFAULT" : domain;
		return getWithTransaction(tx -> readPollData(tx, taskDefName, effectiveDomain));
	}

	@Override
	public List<PollData> getPollData(String taskDefName) {
		Preconditions.checkNotNull(taskDefName, "taskDefName name cannot be null");
		return readAllPollData(taskDefName);
	}

	@Override
	public void addEventPublished(EventPublished ep) {
		getWithTransaction(tx -> insertEventPublished(tx, ep));
	}

	/**
	 * Function to find tasks in the workflows which associated with given tags
	 * <p>
	 * Includes task into result if:
	 * workflow.tags contains ALL values from the tags parameter
	 * and task type matches the given task type
	 * and the task status is IN_PROGRESS
	 *
	 * @param tags A set of tags
	 * @return List of tasks
	 */
	@Override
	public List<Task> getPendingTasksByTags(String taskType, Set<String> tags) {
		String SQL = "SELECT json_data FROM task " +
			"WHERE task_type = ? AND task_status = ? " +
			"AND workflow_id IN (SELECT workflow_id FROM workflow WHERE tags && ?)";

		return queryWithTransaction(SQL, q -> q.addParameter(taskType)
			.addParameter("IN_PROGRESS")
			.addParameter(tags)
			.executeAndFetch(Task.class));
	}

	/**
	 * Function to check is there any workflows associated with given tags
	 * Returns true if workflow.tags contains ALL values from the tags parameter
	 * Otherwise returns false
	 *
	 * @param tags A set of tags
	 * @return Either true or false
	 */
	@Override
	public boolean anyRunningWorkflowsByTags(Set<String> tags) {
		String SQL = "SELECT COUNT(*) FROM workflow WHERE workflow_status = 'RUNNING' AND tags && ?";
		return queryWithTransaction(SQL, q -> q.addParameter(tags).executeScalar(Long.class) > 0);
	}

	@Override
	public void resetStartTime(Task task, boolean updateOutput) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("startTime", task.getStartTime());
		payload.put("endTime", task.getEndTime());
		if (updateOutput) {
			payload.put("outputData", task.getOutputData());
		}

		String SQL = "UPDATE task SET json_data = (json_data::jsonb || ?::jsonb)::text WHERE task_id = ? AND task_status = 'IN_PROGRESS'";
		executeWithTransaction(SQL, q -> q
			.addJsonParameter(payload)
			.addParameter(task.getTaskId())
			.executeUpdate());
	}

	@Override
	public void setWorkflowAttribute(String workflowId, String name, Object value) {
		Map<String, Object> payload = new HashMap<>();
		payload.put(name, value);

		String SQL = "UPDATE workflow " +
				"SET json_data=jsonb_set(json_data::jsonb, '{attributes}', coalesce(json_data::jsonb->'attributes','{}')::jsonb || ?::jsonb)::text " +
				"WHERE workflow_id = ?";
		executeWithTransaction(SQL, q -> q
				.addJsonParameter(payload)
				.addParameter(workflowId)
				.executeUpdate());
	}

	private List<String> getRunningWorkflowIds(Connection tx, String workflowName) {
		String SQL = "SELECT workflow_id FROM workflow WHERE workflow_type = ? AND workflow_status IN ('RUNNING','PAUSED')";
		return query(tx, SQL, q -> q.addParameter(workflowName).executeScalarList(String.class));
	}

	private Task getTask(Connection tx, String taskId) {
		String GET_TASK = "SELECT json_data FROM task WHERE task_id = ?";
		return query(tx, GET_TASK, q -> q.addParameter(taskId).executeAndFetchFirst(Task.class));
	}

	private static int dateStr(Long timeInMs) {
		Date date = new Date(timeInMs);

		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
		return Integer.parseInt(format.format(date));
	}

	private boolean addScheduledTask(Connection tx, Task task) {
		String taskKey = task.getReferenceTaskName() + task.getRetryCount();
		final String CHECK_SQL = "SELECT true FROM task_scheduled WHERE workflow_id = ? AND task_key = ?";
		boolean exists = query(tx, CHECK_SQL, q -> q
			.addParameter(task.getWorkflowInstanceId())
			.addParameter(taskKey)
			.executeScalar(Boolean.class));

		// Return task not scheduled (false) if it is already exists
		if (exists) {
			return false;
		}

		// Warning! Constraint name is also unique index name
		final String ADD_SQL = "INSERT INTO task_scheduled (workflow_id, task_key, task_id) " +
			"VALUES (?, ?, ?) ON CONFLICT ON CONSTRAINT task_scheduled_wf_task DO NOTHING";

		int count = query(tx, ADD_SQL, q -> q
			.addParameter(task.getWorkflowInstanceId())
			.addParameter(taskKey)
			.addParameter(task.getTaskId())
			.executeUpdate());

		return count > 0;
	}

	private void removeTask(Connection tx, Task task) {
		final String taskKey = task.getReferenceTaskName() + task.getRetryCount();

		removeScheduledTask(tx, task, taskKey);
		removeTaskInProgress(tx, task);
		removeTaskData(tx, task);
	}

	private void insertOrUpdateTask(Connection tx, Task task, boolean update) {
		task.setUpdateTime(System.currentTimeMillis());
		if (task.getStatus() != null && task.getStatus().isTerminal()) {
			task.setEndTime(System.currentTimeMillis());
		}

		TaskDef taskDef = metadata.getTaskDef(task.getTaskDefName());
		if (taskDef != null && taskDef.concurrencyLimit() > 0) {
			updateInProgressStatus(tx, task);
		}

		if (update) {
			String SQL = "UPDATE task SET modified_on = now(), task_status = ?, json_data = ?, input = ?, output = ?, start_time = ?, end_time = ? WHERE task_id = ?";
			execute(tx, SQL, q -> q
				.addParameter(task.getStatus().name())
				.addJsonParameter(task)
				.addJsonParameter(task.getInputData())
				.addJsonParameter(task.getOutputData())
				.addTimestampParameter(task.getStartTime())
				.addTimestampParameter(task.getEndTime())
				.addParameter(task.getTaskId())
				.executeUpdate());
		} else {
			String SQL = "INSERT INTO task (task_id, task_type, task_refname, task_status, json_data, workflow_id, " +
				"start_time, end_time, input, output) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT ON CONSTRAINT task_task_id DO NOTHING";
			execute(tx, SQL, q -> q
				.addParameter(task.getTaskId())
				.addParameter(task.getTaskType())
				.addParameter(task.getReferenceTaskName())
				.addParameter(task.getStatus().name())
				.addJsonParameter(task)
				.addParameter(task.getWorkflowInstanceId())
				.addTimestampParameter(task.getStartTime())
				.addTimestampParameter(task.getEndTime())
				.addJsonParameter(task.getInputData())
				.addJsonParameter(task.getOutputData())
				.executeUpdate());
		}

		if (task.getStatus() != null && task.getStatus().isTerminal()) {
			removeTaskInProgress(tx, task);
		}
	}

	private String insertOrUpdateWorkflow(Workflow workflow, boolean update) {
		Preconditions.checkNotNull(workflow, "workflow object cannot be null");

		if (workflow.getStatus().isTerminal()) {
			workflow.setEndTime(System.currentTimeMillis());
		}
		List<Task> tasks = workflow.getTasks();
		workflow.setTasks(Lists.newLinkedList());

		withTransaction(tx -> {
			if (update) {
				updateWorkflow(tx, workflow);
			} else {
				addWorkflow(tx, workflow);
			}
		});

		workflow.setTasks(tasks);

		return workflow.getWorkflowId();
	}

	private void addWorkflow(Connection tx, Workflow workflow) {
		String SQL = "INSERT INTO workflow (workflow_id, parent_workflow_id, workflow_type, workflow_status, " +
			"correlation_id, tags, input, json_data, date_str, start_time) " +
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		execute(tx, SQL, q -> q.addParameter(workflow.getWorkflowId())
			.addParameter(workflow.getParentWorkflowId())
			.addParameter(workflow.getWorkflowType())
			.addParameter(workflow.getStatus().name())
			.addParameter(workflow.getCorrelationId())
			.addParameter(workflow.getTags())
			.addJsonParameter(workflow.getInput())
			.addJsonParameter(workflow)
			.addParameter(dateStr(workflow.getCreateTime()))
			.addTimestampParameter(workflow.getStartTime())
			.executeUpdate());
	}

	private void updateWorkflow(Connection tx, Workflow workflow) {
		String SQL = "UPDATE workflow SET json_data = ?, workflow_status = ?, output = ?, end_time = ?, " +
			"tags = ?, modified_on = now() WHERE workflow_id = ?";

		// We must not delete tags for RESET as it must be restarted right away
		Set<String> tags;
		if (workflow.getStatus().isTerminal() && workflow.getStatus() != Workflow.WorkflowStatus.RESET) {
			tags = Collections.emptySet();
		} else {
			tags = workflow.getTags();
		}

		execute(tx, SQL,
			q -> q.addJsonParameter(workflow)
				.addParameter(workflow.getStatus().name())
				.addJsonParameter(workflow.getOutput())
				.addTimestampParameter(workflow.getEndTime())
				.addParameter(tags)
				.addParameter(workflow.getWorkflowId())
				.executeUpdate());
	}

	private Workflow readWorkflow(Connection tx, String workflowId) {
		String SQL = "SELECT json_data FROM workflow WHERE workflow_id = ?";

		return query(tx, SQL, q -> q.addParameter(workflowId).executeAndFetchFirst(Workflow.class));
	}

	private void removeWorkflow(Connection tx, String workflowId) {
		String SQL = "DELETE FROM workflow WHERE workflow_id = ?";

		execute(tx, SQL, q -> q.addParameter(workflowId).executeDelete());
	}

	private void addTaskInProgress(Connection tx, Task task) {
		String SQL = "INSERT INTO task_in_progress (task_def_name, task_id, workflow_id) VALUES (?, ?, ?) " +
			"ON CONFLICT ON CONSTRAINT task_in_progress_fields DO NOTHING";

		execute(tx, SQL, q -> q.addParameter(task.getTaskDefName())
			.addParameter(task.getTaskId())
			.addParameter(task.getWorkflowInstanceId())
			.executeUpdate());
	}

	private void removeTaskInProgress(Connection tx, Task task) {
		String SQL = "DELETE FROM task_in_progress WHERE task_def_name = ? AND task_id = ?";

		execute(tx, SQL,
			q -> q.addParameter(task.getTaskDefName()).addParameter(task.getTaskId()).executeUpdate());
	}

	private void updateInProgressStatus(Connection tx, Task task) {
		boolean inProgress = Task.Status.IN_PROGRESS.equals(task.getStatus());

		String SQL = "UPDATE task_in_progress SET in_progress = ?, modified_on = now() "
			+ "WHERE task_def_name = ? AND task_id = ?";

		execute(tx, SQL, q -> q.addParameter(inProgress)
			.addParameter(task.getTaskDefName()).addParameter(task.getTaskId()).executeUpdate());
	}

	private void removeScheduledTask(Connection tx, Task task, String taskKey) {
		String SQL = "DELETE FROM task_scheduled WHERE workflow_id = ? AND task_key = ?";

		execute(tx, SQL,
			q -> q.addParameter(task.getWorkflowInstanceId()).addParameter(taskKey).executeDelete());
	}

	private void removeTaskData(Connection tx, Task task) {
		String SQL = "DELETE FROM task WHERE task_id = ?";

		execute(tx, SQL, q -> q.addParameter(task.getTaskId()).executeDelete());
	}

	private boolean insertEventExecution(Connection tx, EventExecution ee) {
		String SQL = "INSERT INTO event_execution" +
			"(handler_name, event_name, message_id, execution_id, status, subject, received_on, accepted_on) " +
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
			"ON CONFLICT ON CONSTRAINT event_execution_fields DO NOTHING";
		int count = query(tx, SQL, q -> q.addParameter(ee.getName())
			.addParameter(ee.getEvent())
			.addParameter(ee.getMessageId())
			.addParameter(ee.getId())
			.addParameter(ee.getStatus().name())
			.addParameter(ee.getSubject())
			.addTimestampParameter(ee.getReceived())
			.addTimestampParameter(ee.getAccepted())
			.executeUpdate());
		return count > 0;
	}

	private void updateEventExecution(Connection tx, EventExecution ee) {
		String SQL = "UPDATE event_execution SET " +
			"modified_on = now(), status = ?, started_on = ?, processed_on = ?" +
			"WHERE handler_name = ? AND event_name = ? " +
			"AND message_id = ? AND execution_id = ?";

		execute(tx, SQL, q -> q.addParameter(ee.getStatus().name())
			.addTimestampParameter(ee.getStarted())
			.addTimestampParameter(ee.getProcessed())
			.addParameter(ee.getName())
			.addParameter(ee.getEvent())
			.addParameter(ee.getMessageId())
			.addParameter(ee.getId())
			.executeUpdate());
	}

	private boolean insertEventPublished(Connection tx, EventPublished ep) {
		String SQL = "INSERT INTO event_published" +
			"(json_data, message_id, subject, published_on) " +
			"VALUES (?, ?, ?, ?)";
		int count = query(tx, SQL, q -> q.addJsonParameter(ep)
			.addParameter(ep.getId())
			.addParameter(ep.getSubject())
			.addTimestampParameter(ep.getPublished())
			.executeUpdate());
		return count > 0;
	}

	private void insertOrUpdatePollData(Connection tx, PollData pollData, String domain) {
		String SQL = "UPDATE poll_data SET json_data=?, modified_on=now() WHERE queue_name=? AND domain=?";
		int count = query(tx, SQL, q -> q.addJsonParameter(pollData)
			.addParameter(pollData.getQueueName())
			.addParameter(domain)
			.executeUpdate());
		if (count == 0) {
			// Warning! Constraint name is also unique index name
			SQL = "INSERT INTO poll_data (queue_name, domain, json_data) VALUES (?, ?, ?) " +
				"ON CONFLICT ON CONSTRAINT poll_data_fields DO NOTHING";
			execute(tx, SQL, q -> q.addParameter(pollData.getQueueName())
				.addParameter(domain)
				.addJsonParameter(pollData)
				.executeUpdate());
		}
	}

	private PollData readPollData(Connection tx, String queueName, String domain) {
		String SQL = "SELECT json_data FROM poll_data WHERE queue_name = ? AND domain = ?";
		return query(tx, SQL,
			q -> q.addParameter(queueName).addParameter(domain).executeAndFetchFirst(PollData.class));
	}

	private List<PollData> readAllPollData(String queueName) {
		String SQL = "SELECT json_data FROM poll_data WHERE queue_name = ?";
		return queryWithTransaction(SQL, q -> q.addParameter(queueName).executeAndFetch(PollData.class));
	}
}
