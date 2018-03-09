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
package com.netflix.conductor.server.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.Map;

/**
 * 
 * @author Oleksiy Lysak
 *
 */
@Singleton
@Path("/v1/status")
@Produces({ MediaType.APPLICATION_JSON })
@Api(value="/v1/status", produces=MediaType.APPLICATION_JSON, tags="Health Check")
public class StatusResource {

	@GET
	@ApiOperation("Get the health check status")
	@Produces({ MediaType.APPLICATION_JSON })
	public Map status()  {
		return Collections.singletonMap("status", "UP");
	}

}
