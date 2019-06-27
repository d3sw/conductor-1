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
package com.netflix.conductor.common.metadata.events;

import com.github.vmg.protogen.annotations.ProtoEnum;
import com.github.vmg.protogen.annotations.ProtoField;
import com.github.vmg.protogen.annotations.ProtoMessage;
import com.google.protobuf.Any;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.*;

/**
 * @author Viren
 * Defines an event handler
 */
@ProtoMessage
public class EventHandler {

	@ProtoField(id = 1)
	@NotEmpty(message = "Missing event handler name")
	private String name;

	@ProtoField(id = 2)
	@NotEmpty(message = "Missing event location")
	private String event;

	@ProtoField(id = 3)
	private String condition;

	@ProtoField(id = 4)
	@NotNull
	@NotEmpty(message = "No actions specified. Please specify at-least one action")
	private List<@Valid Action> actions = new LinkedList<>();

	@ProtoField(id = 5)
	private boolean active;

	@ProtoField(id = 6)
	private String conditionClass;

	@ProtoField(id = 7)
	private String tags;

	@ProtoField(id = 8)
	private boolean retryEnabled;

	public EventHandler() {

	}

	/**
	 * @return the name MUST be unique within a conductor instance
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 *
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the event
	 */
	public String getEvent() {
		return event;
	}

	/**
	 * @param event the event to set
	 *
	 */
	public void setEvent(String event) {
		this.event = event;
	}

	/**
	 * @return the condition
	 */
	public String getCondition() {
		return condition;
	}

	/**
	 * @param condition the condition to set
	 *
	 */
	public void setCondition(String condition) {
		this.condition = condition;
	}

	/**
	 * @return the actions
	 */
	public List<Action> getActions() {
		return actions;
	}

	/**
	 * @param actions the actions to set
	 *
	 */
	public void setActions(List<Action> actions) {
		this.actions = actions;
	}

	/**
	 * @return the active
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * @param active if set to false, the event handler is deactivated
	 *
	 */
	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 *
	 * @return The condition java class
	 */
	public String getConditionClass() {
		return conditionClass;
	}

	/**
	 *
	 * @param conditionClass The condition java class
	 */
	public void setConditionClass(String conditionClass) {
		this.conditionClass = conditionClass;
	}

	/**
	 *
	 * @return The tags JQ expression
	 */
	public String getTags() {
		return tags;
	}

	/**
	 *
	 * @param tags The tags JQ expression
	 */
	public void setTags(String tags) {
		this.tags = tags;
	}

	/**
	 *
	 * @return AMQ retry enabled
	 */
	public boolean isRetryEnabled() {
		return retryEnabled;
	}

	/**
	 *
	 * @param retryEnabled AMQ retry enabled
	 */
	public void setRetryEnabled(boolean retryEnabled) {
		this.retryEnabled = retryEnabled;
	}

	@ProtoMessage
	public static class Action {

		@ProtoEnum
		public enum Type {
			start_workflow, complete_task, fail_task, update_task, find_update, java_action
		}

		@ProtoField(id = 1)
		private Type action;

		@ProtoField(id = 2)
		private StartWorkflow start_workflow;

		@ProtoField(id = 3)
		private TaskDetails complete_task;

		@ProtoField(id = 4)
		private TaskDetails fail_task;

		@ProtoField(id = 5)
		private boolean expandInlineJSON;

		@ProtoField(id = 6)
		private String condition;

		@ProtoField(id = 7)
		private String conditionClass;

		@ProtoField(id = 8)
		private UpdateTask update_task;

		@ProtoField(id = 9)
		private FindUpdate find_update;

		@ProtoField(id = 10)
		private JavaAction java_action;

		/**
		 * @return the action
		 */
		public Type getAction() {
			return action;
		}

		/**
		 * @param action the action to set
		 *
		 */
		public void setAction(Type action) {
			this.action = action;
		}

		/**
		 * @return the start_workflow
		 */
		public StartWorkflow getStart_workflow() {
			return start_workflow;
		}

		/**
		 * @param start_workflow the start_workflow to set
		 *
		 */
		public void setStart_workflow(StartWorkflow start_workflow) {
			this.start_workflow = start_workflow;
		}

		/**
		 * @return the complete_task
		 */
		public TaskDetails getComplete_task() {
			return complete_task;
		}

		/**
		 * @param complete_task the complete_task to set
		 *
		 */
		public void setComplete_task(TaskDetails complete_task) {
			this.complete_task = complete_task;
		}

		/**
		 * @return the fail_task
		 */
		public TaskDetails getFail_task() {
			return fail_task;
		}

		/**
		 * @param fail_task the fail_task to set
		 *
		 */
		public void setFail_task(TaskDetails fail_task) {
			this.fail_task = fail_task;
		}

		/**
		 *
		 * @param expandInlineJSON when set to true, the in-lined JSON strings are expanded to a full json document
		 */
		public void setExpandInlineJSON(boolean expandInlineJSON) {
			this.expandInlineJSON = expandInlineJSON;
		}

		/**
		 *
		 * @return true if the json strings within the payload should be expanded.
		 */
		public boolean isExpandInlineJSON() {
			return expandInlineJSON;
		}

		/**
		 *
		 * @return The condition expression
		 */
		public String getCondition() {
			return condition;
		}

		/**
		 *
		 * @param condition The condition expression
		 */
		public void setCondition(String condition) {
			this.condition = condition;
		}

		/**
		 *
		 * @return The condition class
		 */
		public String getConditionClass() {
			return conditionClass;
		}

		/**
		 *
		 * @param conditionClass The condition class
		 */
		public void setConditionClass(String conditionClass) {
			this.conditionClass = conditionClass;
		}

		/**
		 *
		 * @return Update task params
		 */
		public UpdateTask getUpdate_task() {
			return update_task;
		}

		/**
		 *
		 * @param update_task Update task params
		 */
		public void setUpdate_task(UpdateTask update_task) {
			this.update_task = update_task;
		}

		/**
		 *
		 * @return Find update params
		 */
		public FindUpdate getFind_update() {
			return find_update;
		}

		/**
		 *
		 * @param find_update Find update params
		 */
		public void setFind_update(FindUpdate find_update) {
			this.find_update = find_update;
		}

		/**
		 *
		 * @return Java action params
		 */
		public JavaAction getJava_action() {
			return java_action;
		}

		/**
		 *
		 * @param java_action Java action params
		 */
		public void setJava_action(JavaAction java_action) {
			this.java_action = java_action;
		}

		@Override
		public String toString() {
			return "{action=" + action +
				", start_workflow=" + start_workflow +
				", complete_task=" + complete_task +
				", fail_task=" + fail_task +
				", expandInlineJSON=" + expandInlineJSON +
				", condition='" + condition + '\'' +
				", conditionClass='" + conditionClass + '\'' +
				", update_task=" + update_task +
				", find_update=" + find_update +
				", java_action=" + java_action +
				'}';
		}
	}

	@ProtoMessage
	public static class TaskDetails {

		@ProtoField(id = 1)
		private String workflowId;

		@ProtoField(id = 2)
		private String taskRefName;

		@ProtoField(id = 3)
		private Map<String, Object> output = new HashMap<>();

		@ProtoField(id = 4)
		private Any outputMessage;

		@ProtoField(id = 5)
		private String taskId;

		/**
		 * @return the workflowId
		 */
		public String getWorkflowId() {
			return workflowId;
		}

		/**
		 * @param workflowId the workflowId to set
		 *
		 */
		public void setWorkflowId(String workflowId) {
			this.workflowId = workflowId;
		}

		/**
		 * @return the taskRefName
		 */
		public String getTaskRefName() {
			return taskRefName;
		}

		/**
		 * @param taskRefName the taskRefName to set
		 *
		 */
		public void setTaskRefName(String taskRefName) {
			this.taskRefName = taskRefName;
		}

		/**
		 * @return the output
		 */
		public Map<String, Object> getOutput() {
			return output;
		}

		/**
		 * @param output the output to set
		 *
		 */
		public void setOutput(Map<String, Object> output) {
			this.output = output;
		}

		public Any getOutputMessage() {
			return outputMessage;
		}

		public void setOutputMessage(Any outputMessage) {
			this.outputMessage = outputMessage;
		}

		/**
		 * @return the taskId
		 */
		public String getTaskId() {
			return taskId;
		}

		/**
		 * @param taskId the taskId to set
		 */
		public void setTaskId(String taskId) {
			this.taskId = taskId;
		}

		@Override
		public String toString() {
			return "TaskDetails{" +
				"workflowId='" + workflowId + '\'' +
				", taskRefName='" + taskRefName + '\'' +
				", output=" + output +
				", outputMessage=" + outputMessage +
				", taskId='" + taskId + '\'' +
				'}';
		}
	}

	@ProtoMessage
	public static class StartWorkflow {

		@ProtoField(id = 1)
		private String name;

		@ProtoField(id = 2)
		private Integer version;

		@ProtoField(id = 3)
		private String correlationId;

		@ProtoField(id = 4)
		private Map<String, Object> input = new HashMap<>();

		@ProtoField(id = 5)
		private Any inputMessage;

		@ProtoField(id = 6)
		private Map<String, String> taskToDomain;

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @param name the name to set
		 *
		 */
		public void setName(String name) {
			this.name = name;
		}

		/**
		 * @return the version
		 */
		public Integer getVersion() {
			return version;
		}

		/**
		 * @param version the version to set
		 *
		 */
		public void setVersion(Integer version) {
			this.version = version;
		}


		/**
		 * @return the correlationId
		 */
		public String getCorrelationId() {
			return correlationId;
		}

		/**
		 * @param correlationId the correlationId to set
		 *
		 */
		public void setCorrelationId(String correlationId) {
			this.correlationId = correlationId;
		}

		/**
		 * @return the input
		 */
		public Map<String, Object> getInput() {
			return input;
		}

		/**
		 * @param input the input to set
		 *
		 */
		public void setInput(Map<String, Object> input) {
			this.input = input;
		}

		public Any getInputMessage() {
			return inputMessage;
		}

		public void setInputMessage(Any inputMessage) {
			this.inputMessage = inputMessage;
		}

		public Map<String, String> getTaskToDomain() {
			return taskToDomain;
		}

		public void setTaskToDomain(Map<String, String> taskToDomain) {
			this.taskToDomain = taskToDomain;
		}

		@Override
		public String toString() {
			return "StartWorkflow{" +
				"name='" + name + '\'' +
				", version=" + version +
				", correlationId='" + correlationId + '\'' +
				", input=" + input +
				", inputMessage=" + inputMessage +
				", taskToDomain=" + taskToDomain +
				'}';
		}
	}

	@ProtoMessage
	public static class UpdateTask {
		@ProtoField(id = 1)
		private String workflowId;

		@ProtoField(id = 2)
		private String taskId;

		@ProtoField(id = 3)
		private String taskRef;

		@ProtoField(id = 4)
		private String status;

		@ProtoField(id = 5)
		private String failedReason;

		@ProtoField(id = 6)
		private boolean resetStartTime;

		@ProtoField(id = 7)
		private Map<String, String> statuses = new HashMap<>();

		@ProtoField(id = 8)
		private Map<String, Object> output = new HashMap<>();

		/**
		 *
		 * @return The workflowId JQ expression
		 */
		public String getWorkflowId() {
			return workflowId;
		}

		/**
		 *
		 * @param workflowId The workflowId JQ expression
		 */
		public void setWorkflowId(String workflowId) {
			this.workflowId = workflowId;
		}

		/**
		 *
		 * @return The taskId JQ expression
		 */
		public String getTaskId() {
			return taskId;
		}

		/**
		 *
		 * @param taskId The taskId JQ expression
		 */
		public void setTaskId(String taskId) {
			this.taskId = taskId;
		}

		/**
		 *
		 * @return The taskRef JQ expression
		 */
		public String getTaskRef() {
			return taskRef;
		}

		/**
		 *
		 * @param taskRef The taskRef JQ expression
		 */
		public void setTaskRef(String taskRef) {
			this.taskRef = taskRef;
		}

		/**
		 *
		 * @return The status JQ expression
		 */
		public String getStatus() {
			return status;
		}

		/**
		 *
		 * @param status The status JQ expression
		 */
		public void setStatus(String status) {
			this.status = status;
		}

		/**
		 *
		 * @return The failedReason JQ expression
		 */
		public String getFailedReason() {
			return failedReason;
		}

		/**
		 *
		 * @param failedReason The failedReason JQ expression
		 */
		public void setFailedReason(String failedReason) {
			this.failedReason = failedReason;
		}

		/**
		 *
		 * @return Reset start time for in progress tasks
		 */
		public boolean isResetStartTime() {
			return resetStartTime;
		}

		/**
		 *
		 * @param resetStartTime Reset start time for in progress tasks
		 */
		public void setResetStartTime(boolean resetStartTime) {
			this.resetStartTime = resetStartTime;
		}

		/**
		 *
		 * @return Statuses mapping
		 */
		public Map<String, String> getStatuses() {
			return statuses;
		}

		/**
		 *
		 * @param statuses Statuses mapping
		 */
		public void setStatuses(Map<String, String> statuses) {
			this.statuses = statuses;
		}

		/**
		 *
		 * @return The output payload
		 */
		public Map<String, Object> getOutput() {
			return output;
		}

		/**
		 *
		 * @param output The output payload
		 */
		public void setOutput(Map<String, Object> output) {
			this.output = output;
		}

		@Override
		public String toString() {
			return "UpdateTask{" +
				"workflowId='" + workflowId + '\'' +
				", taskId='" + taskId + '\'' +
				", taskRef='" + taskRef + '\'' +
				", status='" + status + '\'' +
				", resetStartTime=" + resetStartTime +
				", failedReason='" + failedReason + '\'' +
				", statuses=" + statuses +
				", output=" + output +
				'}';
		}
	}

	@ProtoMessage
	public static class FindUpdate {
		@ProtoField(id = 1)
		private String status;

		@ProtoField(id = 2)
		private String failedReason;

		@ProtoField(id = 3)
		private String expression;

		@ProtoField(id = 4)
		private Set<String> taskRefNames = new HashSet<>();

		@ProtoField(id = 5)
		private Map<String, String> statuses = new HashMap<>();

		@ProtoField(id = 6)
		private Map<String, String> inputParameters = new HashMap<>();

		/**
		 *
		 * @return The status JQ expression
		 */
		public String getStatus() {
			return status;
		}

		/**
		 *
		 * @param status The status JQ expression
		 */
		public void setStatus(String status) {
			this.status = status;
		}

		/**
		 *
		 * @return The failedReason JQ expression
		 */
		public String getFailedReason() {
			return failedReason;
		}

		/**
		 *
		 * @param failedReason The failedReason JQ expression
		 */
		public void setFailedReason(String failedReason) {
			this.failedReason = failedReason;
		}

		/**
		 *
		 * @return The JQ expression to match entries
		 */
		public String getExpression() {
			return expression;
		}

		/**
		 *
		 * @param expression The JQ expression to match entries
		 */
		public void setExpression(String expression) {
			this.expression = expression;
		}

		/**
		 *
		 * @return taskRefNames filter
		 */
		public Set<String> getTaskRefNames() {
			return taskRefNames;
		}

		/**
		 *
		 * @param taskRefNames taskRefNames filter
		 */
		public void setTaskRefNames(Set<String> taskRefNames) {
			this.taskRefNames = taskRefNames;
		}

		/**
		 *
		 * @return The statuses mapping
		 */
		public Map<String, String> getStatuses() {
			return statuses;
		}

		/**
		 *
		 * @param statuses The statuses mapping
		 */
		public void setStatuses(Map<String, String> statuses) {
			this.statuses = statuses;
		}

		/**
		 *
		 * @return The inputParameters mapping
		 */
		public Map<String, String> getInputParameters() {
			return inputParameters;
		}

		/**
		 *
		 * @param inputParameters The inputParameters mapping
		 */
		public void setInputParameters(Map<String, String> inputParameters) {
			this.inputParameters = inputParameters;
		}

		@Override
		public String toString() {
			return "FindUpdate{" +
				"taskRefNames=" + taskRefNames +
				", status='" + status + '\'' +
				", statuses=" + statuses +
				", expression='" + expression + '\'' +
				", failedReason='" + failedReason + '\'' +
				", inputParameters='" + inputParameters + '\'' +
				'}';
		}
	}

	@ProtoMessage
	public static class JavaAction {
		@ProtoField(id = 1)
		private String className;

		@ProtoField(id = 2)
		private Map<String, Object> inputParameters;

		/**
		 *
		 * @return The java class name
		 */
		public String getClassName() {
			return className;
		}

		/**
		 *
		 * @param className The java class name
		 */
		public void setClassName(String className) {
			this.className = className;
		}

		/**
		 *
		 * @return The inputParameters mapping for the class
		 */
		public Map<String, Object> getInputParameters() {
			return inputParameters;
		}

		/**
		 *
		 * @param inputParameters The inputParameters mapping for the class
		 */
		public void setInputParameters(Map<String, Object> inputParameters) {
			this.inputParameters = inputParameters;
		}

		@Override
		public String toString() {
			return "JavaAction{" +
				"className='" + className + '\'' +
				", inputParameters=" + inputParameters +
				'}';
		}
	}
}
