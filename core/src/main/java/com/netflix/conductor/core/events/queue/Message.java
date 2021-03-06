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
package com.netflix.conductor.core.events.queue;

import java.util.Map;

/**
 * @author Viren
 *
 */
public class Message {

	private String payload;
	
	private String id;
	
	private String receipt;

	private long received;

	private long accepted;

	private String traceId;

	private int priority;

	private Map<String, String> headers;

	public Message() {
		
	}

	public Message(String id, String payload, String receipt) {
		this.payload = payload;
		this.id = id;
		this.receipt = receipt;
	}

	/**
	 * @return the payload
	 */
	public String getPayload() {
		return payload;
	}

	/**
	 * @param payload the payload to set
	 */
	public void setPayload(String payload) {
		this.payload = payload;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * 
	 * @return Receipt attached to the message
	 */
	public String getReceipt() {
		return receipt;
	}
	
	/**
	 * 
	 * @param receipt Receipt attached to the message
	 */
	public void setReceipt(String receipt) {
		this.receipt = receipt;
	}

	/**
	 *
	 * @return Time when message received
	 */
	public long getReceived() {
		return received;
	}

	/**
	 *
	 * @param received Time when message received
	 */
	public void setReceived(long received) {
		this.received = received;
	}

	/**
	 * @return Time when message accepted at event handler for processing
	 */
	public long getAccepted() {
		return accepted;
	}

	/**
	 * @param accepted Time when message accepted at event handler for processing
	 */
	public void setAccepted(long accepted) {
		this.accepted = accepted;
	}

	/**
	 * @return The Platform Trace Id
	 */
	public String getTraceId() {
		return traceId;
	}

	/**
	 * @param traceId Platform Trace Id
	 */
	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	/**
	 * @return The message priority
	 */
	public int getPriority() { return priority; }

	/**
	 * @param priority  message priority
	 */
	public void setPriority(int priority) { this.priority = priority; }

	/**
	 * @return Additional message headers to be published with
	 */
	public Map<String, String> getHeaders() {
		return headers;
	}

	/**
	 * @param headers Additional message headers to be published with
	 */
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	@Override
	public String toString() {
		return id;
	}
	
}
