/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.common.metadata.workflow;

import com.github.vmg.protogen.annotations.ProtoField;
import com.github.vmg.protogen.annotations.ProtoMessage;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * @author Oleksiy Lysak
 *
 */
@ProtoMessage
public class RerunWorkflowParams {
	@ProtoField(id = 1)
	@NotNull(message = "RerunWorkflowParams name cannot be null")
	@NotEmpty(message = "RerunWorkflowParams name cannot be empty")
	private String name;

	@ProtoField(id = 2)
	private Object version;

	@ProtoField(id = 3)
	private Map<String, Object> input;

	@ProtoField(id = 4)
	private Map<String, String> conditions;

	/**
	 * @return The workflow name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name The workflow name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return The workflow version
	 */
	public Object getVersion() {
		return version;
	}

	/**
	 * @param version The workflow version
	 */
	public void setVersion(Object version) {
		this.version = version;
	}

	/**
	 * @return Rerun input
	 */
	public Map<String, Object> getInput() {
		return input;
	}

	/**
	 * @param input Rerun input
	 */
	public void setInput(Map<String, Object> input) {
		this.input = input;
	}

	/**
	 * @return Rerun conditions
	 */
	public Map<String, String> getConditions() {
		return conditions;
	}

	/**
	 * @param conditions Rerun conditions
	 */
	public void setConditions(Map<String, String> conditions) {
		this.conditions = conditions;
	}
}
