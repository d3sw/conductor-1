package com.netflix.conductor.dao.es6rest.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.MetricsDAO;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Oleksiy Lysak
 */
public class Elasticsearch6RestMetricsDAO extends Elasticsearch6RestAbstractDAO implements MetricsDAO {
    private static final Logger logger = LoggerFactory.getLogger(Elasticsearch6RestMetricsDAO.class);
    private final static String COUNTERS = "COUNTERS";
    private String container;

    @Inject
    public Elasticsearch6RestMetricsDAO(RestHighLevelClient client, Configuration config, ObjectMapper mapper) {
        super(client, config, mapper, "metrics");

        ensureIndexExists(toIndexName(COUNTERS), toTypeName(COUNTERS));
        container = getContainerName();
    }

    public void recordWorkflowStart(Workflow workflow) {
        final String name = prefixName("workflow_start", "sub", workflow.isSubWorkflow());
        counter(name, "workflowName", workflow.getWorkflowType(), "date", LocalDate.now().toString());
    }

    private void counter(String name, String... additionalTags) {
        String indexName = toIndexName(COUNTERS);
        String typeName = toTypeName(COUNTERS);
        String id = toId(name);

        Map<String, Object> payload = ImmutableMap.of("container", container,
                "name", name,
                "tags", toTags(additionalTags));

        upsert(indexName, typeName, id, payload);
    }

    private static Map<String, String> toTags(String... additionalTags) {
        Map<String, String> tags = new HashMap<>();
        for (int j = 0; j < additionalTags.length - 1; j++) {
            String tk = additionalTags[j];
            String tv = "" + additionalTags[j + 1];
            if(!tv.isEmpty()) {
                tags.put(tk, tv);
            }
            j++;
        }
        return tags;
    }

    private static String prefixName(String name, String prefix, boolean condition) {
        if (condition) {
            return prefix + name;
        }

        return name;
    }

    private String getContainerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "unknown";
        }
    }
}