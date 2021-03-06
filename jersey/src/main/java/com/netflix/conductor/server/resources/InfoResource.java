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
package com.netflix.conductor.server.resources;

import com.google.common.collect.ImmutableMap;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.MetricsDAO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Oleksiy Lysak
 *
 */
@Singleton
@Path("/v1")
@Api(value = "/v1", produces = MediaType.APPLICATION_JSON, tags = "Status Info")
@Produces({MediaType.APPLICATION_JSON})
public class InfoResource {
	private static final Logger logger = LoggerFactory.getLogger(InfoResource.class);
	private final MetricsDAO metricsDAO;
	private final Configuration config;
	private String fullVersion;

	@Inject
	public InfoResource(Configuration config, MetricsDAO metricsDAO) {
		this.config = config;
		this.metricsDAO = metricsDAO;
		try {
			InputStream propertiesIs = this.getClass().getClassLoader().getResourceAsStream("META-INF/conductor-core.properties");
			Properties prop = new Properties();
			prop.load(propertiesIs);
			String version = prop.getProperty("Implementation-Version");
			String change = prop.getProperty("Change");
			fullVersion = config.getProperty("APP.VERSION", version + "-" + change);
		} catch (Exception e) {
			logger.error("Failed to read conductor-core.properties" + e.getMessage(), e);
		}
	}

	@GET
	@Path("/status")
	@ApiOperation(value = "Get the status")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> status() {
		return Collections.singletonMap("version", fullVersion);
	}

	@GET
	@Path("/health")
	@ApiOperation(value = "Get the health status")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> health() throws IOException {
		boolean status = false;

		try {
			status = metricsDAO.ping();
		} catch (Exception e) {
			logger.error("Db health check failed: " + e.getMessage(), e);
			throw e;
		}

		return Collections.singletonMap("is_ping_okay", status);
	}

	@GET
	@Path("/stuckChecksums")
	@ApiOperation(value = "Get the list of checksum jobs that are potentially stuck")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> stuckChecksums(){
		try {
			Calendar threeDaysAgo = Calendar.getInstance();
			threeDaysAgo.add(Calendar.DATE, -3);

			Calendar thirtyMinsAgo = Calendar.getInstance();
			thirtyMinsAgo.add(Calendar.MINUTE, -30);

			return metricsDAO.getStuckChecksums(threeDaysAgo.getTimeInMillis(), thirtyMinsAgo.getTimeInMillis());
		} catch (Exception e) {
			logger.error("Error while fetching checksum info " + e.getMessage(), e);
			throw e;
		}
	}

	@GET
	@Path("/dependencies")
	@ApiOperation(value = "Get the dependencies")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> dependencies() {
		List<Object> endpoints = new ArrayList<>();
		endpoints.add(config.getProperty("conductor.auth.url", ""));
		endpoints.add("events.service." + config.getProperty("TLD", "local"));
		endpoints.add("vault.service." + config.getProperty("TLD", "local"));

		List<Map<String, Object>> dependencies = new ArrayList<>();
		dependencies.add(ImmutableMap.<String, Object>builder()
			.put("name", "auth")
			.put("version", "v1")
			.put("scheme", "https")
			.put("external", false)
			.build());
		dependencies.add(ImmutableMap.<String, Object>builder()
			.put("name", "vault")
			.put("version", "v1")
			.put("scheme", "http")
			.put("external", false)
			.build());
		dependencies.add(ImmutableMap.<String, Object>builder()
			.put("name", "shotgun")
			.put("version", "v1")
			.put("scheme", "amq")
			.put("external", false)
			.build());
		dependencies.add(ImmutableMap.<String, Object>builder()
			.put("name", "*")
			.put("version", "v1")
			.put("scheme", "http")
			.put("external", false)
			.build());

		Map<String, Object> result = new HashMap<>();
		result.put("version", fullVersion);
		result.put("endpoints", endpoints);
		result.put("dependencies", dependencies);
		return result;
	}

	@GET
	@Path("/metrics")
	@ApiOperation(value = "Get the metrics")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> metrics() {
		return metricsDAO.getMetrics();
	}
}
