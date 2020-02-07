package com.netflix.conductor.contribs.taskupdate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.events.JavaEventAction;
import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.*;

@Singleton
public class TaskUpdateHandler implements JavaEventAction {
	private static Logger logger = LoggerFactory.getLogger(TaskUpdateHandler.class);
	private static final String JQ_GET_WFID_URN = ".urns[] | select(startswith(\"urn:deluxe:conductor:workflow:\")) | split(\":\") [4]";
	private final WorkflowExecutor executor;
	private final ObjectMapper mapper;

	@Inject
	public TaskUpdateHandler(WorkflowExecutor executor, ObjectMapper mapper) {
		this.executor = executor;
		this.mapper = mapper;
	}

	@Override
	public ArrayList<String> handle(EventHandler.Action action, Object payload, EventExecution ee) throws Exception {
		ActionParams params = mapper.convertValue(action.getJava_action().getInputParameters(), ActionParams.class);
		if (StringUtils.isEmpty(params.taskRefName)) {
			throw new IllegalStateException("No taskRefName defined in parameters");
		}
		ArrayList<String> workflowIds = new ArrayList<String>();
		Map<String, Object> eventpayload = mapper.convertValue(payload, Map.class);;

		return workflowIds;
	}

	// Keep fields public!
	public static class ActionParams {
		public String taskRefName;
		public String workflowIdJq;
	}
}
