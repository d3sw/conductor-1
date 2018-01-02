package com.netflix.conductor.contribs.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Output {



	private Map<String, String> conditions;


	public Map<String, String> getConditions() {
		return conditions;
	}

	/**
	 * @param conditions the method to set
	 */
	public void setConditions(Map<String, String> conditions) {
		this.conditions = conditions;
	}


}
