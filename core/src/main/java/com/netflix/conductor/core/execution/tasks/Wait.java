/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.TaskUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

/**
 * @author Viren
 *
 */
public class Wait extends WorkflowSystemTask {

	public static final String NAME = "WAIT";

	public Wait() {
		super(NAME);
	}

	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		if (!execute(workflow, task, executor)) {
			task.setStatus(Status.IN_PROGRESS);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean execute(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		Map<String, Object> eventWait = (Map<String, Object>)task.getInputData().get("event_wait");
		if (MapUtils.isEmpty(eventWait)) {
			return false;
		}
		Parameters params = executor.getMapper().convertValue(eventWait, Parameters.class);
		Message msg = executor.findMessageByQuery(params.query);
		if (msg == null) {
			return false;
		}

		Object payload = null;
		if (msg.getPayload() != null) {
			try {
				payload = executor.getMapper().readValue(msg.getPayload(), Object.class);
			} catch (Exception e) {
				payload = msg.getPayload();
			}
		}

		Task.Status taskStatus;
		if (StringUtils.isNotEmpty(params.update.status)) {
			// Get an evaluating which might result in error or empty response
			String status = ScriptEvaluator.evalJq(params.update.status, payload);
			if (StringUtils.isEmpty(status))
				throw new RuntimeException("Unable to determine status. Check mapping and payload");

			// If mapping exists - take the task status from mapping
			if (MapUtils.isNotEmpty(params.update.statuses)) {
				status = params.update.statuses.get(status);
				taskStatus = TaskUtils.getTaskStatus(status);
			} else {
				taskStatus = TaskUtils.getTaskStatus(status);
			}
		} else {
			taskStatus = Task.Status.COMPLETED;
		}

		task.setStatus(taskStatus);
		task.getOutputData().put("conductor.event.payload", payload);
		task.getOutputData().put("conductor.event.messageId", msg.getId());

		// Set the reason if task failed. It should be provided in the event
		if (Task.Status.FAILED.equals(taskStatus)) {
			String failedReason = null;
			if (StringUtils.isNotEmpty(params.update.failedReason)) {
				failedReason = ScriptEvaluator.evalJq(params.update.failedReason, payload);
			}
			task.setReasonForIncompletion(failedReason);
		}

		return true;
	}

	@Override
	public void cancel(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		task.setStatus(Status.CANCELED);
	}

	private static class Parameters {
		public UpdateParameters update;
		public Map<String, Object> query;
	}

	private static class UpdateParameters {
		public String status;
		public String failedReason;
		public Map<String, String> statuses;
	}
}
