/**
 * Copyright 2017 Netflix, Inc.
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
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.ExecutionDAO;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Oleksiy Lysak
 */
public class GetWorkflowStatus extends WorkflowSystemTask {
	private static final Logger logger = LoggerFactory.getLogger(GetWorkflowStatus.class);

	public GetWorkflowStatus() {
		super("GET_WORKFLOW_STATUS");
	}

	@Override
	public void start(Workflow workflow, Task task, WorkflowExecutor executor) throws Exception {
		try {
			String workflowId = (String)task.getInputData().get("workflowId");
			if (StringUtils.isEmpty(workflowId))
				throw new IllegalArgumentException("No workflowId provided in parameters");

			ExecutionDAO executionDao = executor.getExecutionDao();
			Workflow.WorkflowStatus status = executionDao.getWorkflowStatus(workflowId);
			if (status == null) {
				task.setStatus(Task.Status.FAILED);
				task.setReasonForIncompletion("No workflow found with id " + workflowId);
				return;
			}

			task.getOutputData().put("status", status);
			task.setStatus(Task.Status.COMPLETED);
		} catch (Exception e) {
			task.setStatus(Task.Status.FAILED);
			task.setReasonForIncompletion(e.getMessage());
			logger.error(e.getMessage(), e);
		}
	}
}
