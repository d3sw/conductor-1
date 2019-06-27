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
import com.netflix.conductor.common.metadata.events.EventHandler.Action;

import java.util.*;

/**
 * @author Viren
 *
 */
@ProtoMessage
public class EventExecution {

	@ProtoEnum
	public enum Status {
		IN_PROGRESS, COMPLETED, FAILED, SKIPPED
	}

	@ProtoField(id = 1)
	private String id;

	@ProtoField(id = 2)
	private String messageId;

	@ProtoField(id = 3)
	private String name;

	@ProtoField(id = 4)
	private String event;

	@ProtoField(id = 5)
	private long created;

	@ProtoField(id = 6)
	private Status status;

	@ProtoField(id = 7)
	private Action.Type action;

	@ProtoField(id = 8)
	private Map<String, Object> output = new HashMap<>();

	@ProtoField(id = 9)
	private String subject;

	@ProtoField(id = 10)
	private long received;

	@ProtoField(id = 11)
	private long processed;

	@ProtoField(id = 12)
	private long started;

	@ProtoField(id = 13)
	private long accepted;

	@ProtoField(id = 14)
	private Set<String> tags = new HashSet<>();

	public EventExecution() {

	}
	
	public EventExecution(String id, String messageId) {
		this.id = id;
		this.messageId = messageId;
	}
	
	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 * 
	 */
	public void setId(String id) {
		this.id = id;
	}

	
	/**
	 * @return the messageId
	 */
	public String getMessageId() {
		return messageId;
	}

	/**
	 * @param messageId the messageId to set
	 * 
	 */
	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}

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
	 * @return the created
	 */
	public long getCreated() {
		return created;
	}

	/**
	 * @param created the created to set
	 * 
	 */
	public void setCreated(long created) {
		this.created = created;
	}

	/**
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * @param status the status to set
	 * 
	 */
	public void setStatus(Status status) {
		this.status = status;
	}

	/**
	 * @return the action
	 */
	public Action.Type getAction() {
		return action;
	}

	/**
	 * @param action the action to set
	 * 
	 */
	public void setAction(Action.Type action) {
		this.action = action;
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

	/**
	 *
	 * @return the event subject
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 *
	 * @param subject the event subject
	 */
	public void setSubject(String subject) {
		this.subject = subject;
	}

	/**
	 *
	 * @return the execution received time
	 */
	public long getReceived() {
		return received;
	}

	/**
	 *
	 * @param received the execution received time
	 */
	public void setReceived(long received) {
		this.received = received;
	}

	/**
	 *
	 * @return the execution processed time
	 */
	public long getProcessed() {
		return processed;
	}

	/**
	 *
	 * @param processed the execution processed time
	 */
	public void setProcessed(long processed) {
		this.processed = processed;
	}

	/**
	 *
	 * @return the execution started time
	 */
	public long getStarted() {
		return started;
	}

	/**
	 *
	 * @param started the execution started time
	 */
	public void setStarted(long started) {
		this.started = started;
	}

	/**
	 *
	 * @return the event accepted time
	 */
	public long getAccepted() {
		return accepted;
	}

	/**
	 *
	 * @param accepted the event accepted time
	 */
	public void setAccepted(long accepted) {
		this.accepted = accepted;
	}

	/**
	 *
	 * @return the execution tags associated with
	 */
	public Set<String> getTags() {
		return tags;
	}

	/**
	 *
	 * @param tags the execution tags associated with
	 */
	public void setTags(Set<String> tags) {
		this.tags = tags;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EventExecution execution = (EventExecution) o;
		return created == execution.created &&
				Objects.equals(id, execution.id) &&
				Objects.equals(messageId, execution.messageId) &&
				Objects.equals(name, execution.name) &&
				Objects.equals(event, execution.event) &&
				status == execution.status &&
				action == execution.action &&
				Objects.equals(output, execution.output);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, messageId, name, event, created, status, action, output);
	}

}
