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

import com.github.vmg.protogen.annotations.ProtoField;
import com.github.vmg.protogen.annotations.ProtoMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Oleksiy Lysak
 *
 */
@ProtoMessage
public class EventPublished {

	@ProtoField(id = 1)
	private String id;

	@ProtoField(id = 2)
	private String type;

	@ProtoField(id = 3)
	private String subject;

	@ProtoField(id = 4)
	private long published;

	@ProtoField(id = 5)
	private Map<String, Object> payload = new HashMap<>();

	/**
	 *
	 * @return the message id
	 */
	public String getId() {
		return id;
	}

	/**
	 *
	 * @param id the message id
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 *
	 * @return the event type
	 */
	public String getType() {
		return type;
	}

	/**
	 *
	 * @param type the event type
	 */
	public void setType(String type) {
		this.type = type;
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
	 * @return the published time
	 */
	public long getPublished() {
		return published;
	}

	/**
	 *
	 * @param published the published time
	 */
	public void setPublished(long published) {
		this.published = published;
	}

	/**
	 *
	 * @return the payload object
	 */
	public Map<String, Object> getPayload() {
		return payload;
	}

	/**
	 *
	 * @param payload the payload object
	 */
	public void setPayload(Map<String, Object> payload) {
		this.payload = payload;
	}
}
