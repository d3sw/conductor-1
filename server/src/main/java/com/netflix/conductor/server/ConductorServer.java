/**
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.conductor.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.servlet.GuiceFilter;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.utils.WaitUtils;
import com.netflix.conductor.dao.es5.es.EmbeddedElasticSearch;
import com.netflix.conductor.dns.DNSLookup;
import com.netflix.conductor.redis.utils.JedisMock;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.Host.Status;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.connectionpool.impl.lb.HostToken;
import com.netflix.dyno.connectionpool.impl.utils.CollectionUtils;
import com.netflix.dyno.connectionpool.impl.utils.ConfigUtils;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.sun.jersey.api.client.Client;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisCommands;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * @author Viren
 *
 */
public class ConductorServer {

	private static Logger logger = LoggerFactory.getLogger(ConductorServer.class);

	private enum DB {
		redis, dynomite, memory
	}

	private enum SearchMode {
		elasticsearch, memory
	}

	private ServerModule sm;

	private Server server;

	private ConductorConfig cc;

	private DB db;

	private SearchMode mode;

	private String dbDnsService;

	public ConductorServer(ConductorConfig cc) {
		this.cc = cc;
		String dynoClusterName = cc.getProperty("workflow.dynomite.cluster.name", "");

		List<Host> dynoHosts = new LinkedList<>();
		String dbstring = cc.getProperty("db", "memory");
		try {
			db = DB.valueOf(dbstring);
		} catch (IllegalArgumentException ie) {
			logger.error("Invalid db name: " + dbstring + ", supported values are: redis, dynomite, memory");
			System.exit(1);
		}

		String modestring = cc.getProperty("workflow.elasticsearch.mode", "memory");
		try {
			mode = SearchMode.valueOf(modestring);
		} catch (IllegalArgumentException ie) {
			logger.error("Invalid setting for workflow.elasticsearch.mode: " + modestring + ", supported values are: elasticsearch, memory");
			System.exit(1);
		}

		if (!db.equals(DB.memory)) {
			dbDnsService = cc.getProperty("workflow.dynomite.cluster.service", null);
			if (StringUtils.isNotEmpty(dbDnsService)) {
				logger.info("Using dns service {} to setup db cluster", dbDnsService);

				int connectAttempts = cc.getIntProperty("workflow.dynomite.dnslookup.attempts", 60);
				int connectSleepSecs = cc.getIntProperty("workflow.dynomite.dnslookup.sleep.seconds", 1);

				// Run dns lookup in the waiter wrapper
				WaitUtils.wait("dnsLookup(dynomite)", connectAttempts, connectSleepSecs, () -> {
					List<Host> nodes = lookupNodes(cc, dbDnsService);
					nodes.forEach(node -> {
						logger.info("Adding {} to the db configuration", node);
						dynoHosts.add(node);
					});
					return true;
				});

				logger.info("Dns lookup done");
			} else {
				logger.info("Using provided hosts to setup db cluster");

				String hosts = cc.getProperty("workflow.dynomite.cluster.hosts", null);
				if (hosts == null) {
					System.err.println("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.");
					logger.error("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.");
					System.exit(1);
				}
				String[] hostConfigs = hosts.split(";");

				for (String hostConfig : hostConfigs) {
					String[] hostConfigValues = hostConfig.split(":");
					String host = hostConfigValues[0];
					int port = Integer.parseInt(hostConfigValues[1]);
					String rack = hostConfigValues[2];
					Host dynoHost = new Host(host, port, rack, Status.Up);
					dynoHosts.add(dynoHost);
				}
			}

		} else {
			//Create a single shard host supplier
			Host dynoHost = new Host("localhost", 0, cc.getAvailabilityZone(), Status.Up);
			dynoHosts.add(dynoHost);
		}
		init(dynoClusterName, dynoHosts);
	}

	private void init(String dynoClusterName, List<Host> dynoHosts) {
		final HostSupplier hostSupplier = () -> dynoHosts;
		JedisCommands jedis = null;
		switch (db) {
			case redis:
			case dynomite:
				String enabled = cc.getProperty("workflow.dynomite.cluster.enabled", "false");
				if (Boolean.parseBoolean(enabled)) {
					logger.info("Using jedis cluster api per enabled configuration");
					Set<HostAndPort> hosts = dynoHosts.stream()
						 .map(host -> new HostAndPort(host.getHostName(), host.getPort()))
						 .collect(toSet());
					jedis = new JedisCluster(hosts);
				} else {
					// Otherwise go with dyno api
					final Set<HostToken> tokens = new HashSet<>();
					dynoHosts.forEach(host -> {
						HostToken token = new HostToken(tokens.size() + 1L, host);
						tokens.add(token);
					});

					final TokenMapSupplier tokenSupplier = new TokenMapSupplier() {
						@Override
						public List<HostToken> getTokens(Set<Host> activeHosts) {
							return new ArrayList<>(tokens);
						}

						@Override
						public HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
							return CollectionUtils.find(tokens, x -> x.getHost().equals(host));
						}
					};

					ConnectionPoolConfigurationImpl cp = new ConnectionPoolConfigurationImpl(dynoClusterName)
							.withTokenSupplier(tokenSupplier)
							.setLocalRack(cc.getAvailabilityZone())
							.setLocalDataCenter(cc.getRegion());

					jedis = new DynoJedisClient.Builder()
							.withHostSupplier(hostSupplier)
							.withApplicationName(cc.getAppId())
							.withDynomiteClusterName(dynoClusterName)
							.withCPConfig(cp)
							.build();
				}

				JedisCommands lambda = jedis; // Required for lambda wrapper only
				int connectAttempts = cc.getIntProperty("workflow.dynomite.connection.attempts", 60);
				int connectSleepSecs = cc.getIntProperty("workflow.dynomite.connection.sleep.seconds", 1);
				WaitUtils.wait("dynomite", connectAttempts, connectSleepSecs, () -> {
					// In response, the echo should return the 'ping'
					String response = lambda.echo("ping");
					return "ping".equalsIgnoreCase(response);
				});

//				// Start the dns service monitor
//				if (StringUtils.isNotEmpty(dbDnsService)) {
//					int monitorDelay = cc.getIntProperty("workflow.dynomite.monitor.delay", 30);
//					int monitorPeriod = cc.getIntProperty("workflow.dynomite.monitor.period.seconds", 3);
//					try {
//						Executors.newScheduledThreadPool(1)
//								.scheduleAtFixedRate(() -> monitor(jedisClient, dbDnsService), monitorDelay, monitorPeriod, TimeUnit.SECONDS);
//					} catch (Exception e) {
//						logger.error("Unable to start dynomite service monitor: {}", e.getMessage(), e);
//					}
//				}
//				jedis = dynoClient;
				logger.info("Starting conductor server using dynomite cluster " + dynoClusterName);
				break;

			case memory:
				jedis = new JedisMock();
		}

		switch (mode) {
			case memory:

				try {
					EmbeddedElasticSearch.start();
					if (cc.getProperty("workflow.elasticsearch.url", null) == null) {
						System.setProperty("workflow.elasticsearch.url", "localhost:9300");
					}
					if (cc.getProperty("workflow.elasticsearch.index.name", null) == null) {
						System.setProperty("workflow.elasticsearch.index.name", "conductor");
					}
				} catch (Exception e) {
					logger.error("Error starting embedded elasticsearch.  Search functionality will be impacted: " + e.getMessage(), e);
				}
				logger.info("Starting conductor server using in memory data store");
				break;

			case elasticsearch:
				break;
		}

		this.sm = new ServerModule(jedis, hostSupplier, cc);
	}

	private void monitor(DynoJedisClient client, String dnsService) {
		try {
			List<Host> resolved = lookupNodes(cc, dbDnsService);
			logger.debug("Resolved nodes=" + resolved);

		} catch (Exception ex) {
			logger.error("Monitor failed: " + ex.getMessage(), ex);
		}
	}

	private List<Host> lookupNodes(ConductorConfig cc, String dnsService) {
		DNSLookup lookup = new DNSLookup();
		DNSLookup.DNSResponses responses = lookup.lookupService(dnsService);
		if (responses.getResponses() == null || responses.getResponses().length == 0)
			throw new RuntimeException("Unable to lookup service. No records found for " + dnsService);

		String rack = cc.getAvailabilityZone();
		String datacenter = ConfigUtils.getDataCenterFromRack(rack);
		List<Host> hosts = new ArrayList<>(responses.getResponses().length);
		for(DNSLookup.DNSResponse response : responses.getResponses()){
			Host host = new Host(response.getHostName(), response.getAddress(), response.getPort(), rack, datacenter,
					Status.Up);
			hosts.add(host);
		}
		return hosts;
	}

	public ServerModule getGuiceModule() {
		return sm;
	}

	public synchronized void start(int port, boolean join) throws Exception {

		if (server != null) {
			throw new IllegalStateException("Server is already running");
		}

		Guice.createInjector(sm);

		//Swagger
		String resourceBasePath = Main.class.getResource("/swagger-ui").toExternalForm();
		this.server = new Server(port);

		ServletContextHandler context = new ServletContextHandler();
		context.addFilter(GuiceFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
		context.setResourceBase(resourceBasePath);
		context.setWelcomeFiles(new String[]{"index.html"});

		server.setHandler(context);


		DefaultServlet staticServlet = new DefaultServlet();
		context.addServlet(new ServletHolder(staticServlet), "/*");

		server.start();
		System.out.println("Started server on http://localhost:" + port + "/");
		try {
			boolean create = Boolean.parseBoolean(System.getenv("loadSample"));
			if (create) {
				System.out.println("Creating kitchensink workflow");
				createKitchenSink(port);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		if (join) {
			server.join();
		}

	}

	public synchronized void stop() throws Exception {
		if (server == null) {
			throw new IllegalStateException("Server is not running.  call #start() method to start the server");
		}
		server.stop();
		server = null;
	}

	private static void createKitchenSink(int port) throws Exception {

		List<TaskDef> taskDefs = new LinkedList<>();
		for (int i = 0; i < 40; i++) {
			taskDefs.add(new TaskDef("task_" + i, "task_" + i, 1, 0));
		}
		taskDefs.add(new TaskDef("search_elasticsearch", "search_elasticsearch", 1, 0));

		Client client = Client.create();
		ObjectMapper om = new ObjectMapper();
		client.resource("http://localhost:" + port + "/api/metadata/taskdefs").type(MediaType.APPLICATION_JSON).post(om.writeValueAsString(taskDefs));

		InputStream stream = Main.class.getResourceAsStream("/kitchensink.json");
		client.resource("http://localhost:" + port + "/api/metadata/workflow").type(MediaType.APPLICATION_JSON).post(stream);

		stream = Main.class.getResourceAsStream("/sub_flow_1.json");
		client.resource("http://localhost:" + port + "/api/metadata/workflow").type(MediaType.APPLICATION_JSON).post(stream);

		String input = "{\"task2Name\":\"task_5\"}";
		client.resource("http://localhost:" + port + "/api/workflow/kitchensink").type(MediaType.APPLICATION_JSON).post(input);

		logger.info("Kitchen sink workflows are created!");
	}
}
