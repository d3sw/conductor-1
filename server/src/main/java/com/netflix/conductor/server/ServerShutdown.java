package com.netflix.conductor.server;

import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.execution.WorkflowSweeper;
import com.netflix.conductor.core.execution.batch.BatchSweeper;
import com.netflix.conductor.core.execution.tasks.SystemTaskWorkerCoordinator;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

@Singleton
public class ServerShutdown {
	private static Logger logger = LoggerFactory.getLogger(ServerShutdown.class);
	private EventProcessor eventProcessor;
	private WorkflowSweeper workflowSweeper;
	private SystemTaskWorkerCoordinator taskWorkerCoordinator;
	private BatchSweeper batchSweeper;
	private DataSource dataSource;

	@Inject
	public ServerShutdown(EventProcessor eventProcessor,
						  WorkflowSweeper workflowSweeper,
						  SystemTaskWorkerCoordinator taskWorkerCoordinator,
						  BatchSweeper batchSweeper,
						  DataSource dataSource) {
		this.eventProcessor = eventProcessor;
		this.workflowSweeper = workflowSweeper;
		this.taskWorkerCoordinator = taskWorkerCoordinator;
		this.batchSweeper = batchSweeper;
		this.dataSource = dataSource;

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				shutdown();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}));
	}

	private void shutdown() {
		batchSweeper.shutdown();
		eventProcessor.shutdown();
		workflowSweeper.shutdown();
		taskWorkerCoordinator.shutdown();

		logger.info("Closing primary data source");
		if (dataSource instanceof HikariDataSource) {
			((HikariDataSource) dataSource).close();
		}
	}
}
