package com.netflix.conductor.core.events;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.NDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.UnsupportedOperationException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class RetryQueueManager {
    private static Logger logger = LoggerFactory.getLogger(RetryQueueManager.class);
    private AmazonSQS sqs;
    private String queueURL;
    private boolean enabled;
    private int threadPoolSize;
    private int visibilityTimeout;
    private ExecutorService executors;
    private WorkflowExecutor executor;
    private ObjectMapper mapper = new ObjectMapper();

    @Inject
    public RetryQueueManager(Configuration config, WorkflowExecutor executor) {
        this.executor = executor;
        this.enabled = Boolean.parseBoolean(config.getProperty("event.processor.enable.retries", "false"));
        if (!this.enabled) {
            return;
        }
        String accessKey = config.getProperty("aws.access.key", null);
        String secretKey = config.getProperty("aws.secret.key", null);
        String regionKey = config.getProperty("aws.region", null);
        if (StringUtils.isAnyEmpty(accessKey, secretKey, regionKey)) {
            logger.warn("Retry queue manager will be disabled. Check 'aws.access.key', 'aws.secret.key', 'aws.region'");
            this.enabled = false;
        } else {
            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);

            this.sqs = AmazonSQSClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                    .withRegion(regionKey)
                    .build();

            String queueName = config.getProperty("event.processor.retry.queue", "conductor_event_retry")
                    + "_" + config.getStack();

            this.queueURL = fetchQueueUrls(queueName);

            this.threadPoolSize = config.getIntProperty("event.processor.retry.thread.count", 2);
            this.executors = Executors.newFixedThreadPool(threadPoolSize);

            this.visibilityTimeout = config.getIntProperty("event.processor.retry.visibility.timeout", 60);

            int initialDelay = config.getIntProperty("event.processor.retry.initial.delay", 60);
            int poolFrequency = config.getIntProperty("event.processor.retry.pool.frequency", 1000);

            Executors.newScheduledThreadPool(1).scheduleWithFixedDelay(this::poolAndExecute, initialDelay, poolFrequency, TimeUnit.MILLISECONDS);
        }
    }

    void enqueue(EventHandler.Action action, Object payload, String event, String eventId) throws Exception {
        if (!enabled) {
            return;
        }
        EventWrapper wrapper = new EventWrapper();
        wrapper.eventId = eventId;
        wrapper.payload = payload;
        wrapper.action = action;
        wrapper.event = event;
        wrapper.retried = 0;

        enqueue(wrapper, action.getRetryDelay());
    }

    private void enqueue(EventWrapper wrapper, int delaySeconds) throws Exception {
        wrapper.messageId = UUID.randomUUID().toString();

        SendMessageBatchRequestEntry request = new SendMessageBatchRequestEntry();
        request.setId(wrapper.messageId);
        request.setMessageBody(mapper.writeValueAsString(wrapper));
        request.setDelaySeconds(delaySeconds);

        SendMessageBatchRequest batch = new SendMessageBatchRequest(queueURL);
        batch.getEntries().add(request);
        sqs.sendMessageBatch(batch);
    }

    private List<EventWrapper> receive(int size, int visibilityTimeout) {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                .withQueueUrl(queueURL)
                .withVisibilityTimeout(visibilityTimeout)
                .withMaxNumberOfMessages(size);

        ReceiveMessageResult result = sqs.receiveMessage(receiveMessageRequest);
        return result.getMessages().stream()
                .map(msg -> {
                    try {
                        EventWrapper wrapper = mapper.readValue(msg.getBody(), EventWrapper.class);
                        wrapper.messageId = msg.getMessageId();
                        wrapper.receiptHandle = msg.getReceiptHandle();

                        return wrapper;
                    } catch (IOException e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                })
                .collect(Collectors.toList());
    }

    private void delete(EventWrapper message) {
        DeleteMessageBatchRequest batch = new DeleteMessageBatchRequest().withQueueUrl(queueURL);
        List<DeleteMessageBatchRequestEntry> entries = batch.getEntries();

        entries.add(new DeleteMessageBatchRequestEntry().withId(message.messageId).withReceiptHandle(message.receiptHandle));

        sqs.deleteMessageBatch(batch);
    }

    private String fetchQueueUrls(String queueName) {
        ListQueuesRequest listQueuesRequest = new ListQueuesRequest().withQueueNamePrefix(queueName);
        ListQueuesResult listQueuesResult = sqs.listQueues(listQueuesRequest);
        List<String> queueUrls = listQueuesResult.getQueueUrls().stream().filter(u -> u.contains(queueName)).collect(Collectors.toList());
        if (queueUrls.isEmpty()) {
            CreateQueueRequest createQueueRequest = new CreateQueueRequest().withQueueName(queueName);
            CreateQueueResult result = sqs.createQueue(createQueueRequest);
            return result.getQueueUrl();
        } else {
            return queueUrls.get(0);
        }
    }

    private void poolAndExecute() {
        try {
            List<EventWrapper> messages = receive(threadPoolSize, visibilityTimeout);
            for(EventWrapper wrapper : messages) {
                try {
                    executors.submit(() -> {
                        NDC.push("event-retry-"+ UUID.randomUUID().toString());
                        try {
                            logger.info("About to retry action={}, payload={}, event={}, messageId={}, retried={}",
                                    wrapper.action, wrapper.payload, wrapper.event, wrapper.eventId, wrapper.retried);

                            // Execute action
                            execute(wrapper.action, wrapper.payload, wrapper.event, wrapper.eventId);
                            wrapper.retried++;

                            // Delete from queue
                            delete(wrapper);

                            // Need to requeue ?
                            if (wrapper.retried < wrapper.action.getRetryCount()) {
                                enqueue(wrapper, wrapper.action.getRetryDelay());
                            }
                        } catch (Exception ex) {
                            logger.error("Execute failed " + ex.getMessage() + " for " + wrapper, ex);
                        } finally {
                            NDC.remove();
                        }
                    });
                } catch (RejectedExecutionException ex) {
                    logger.error("All workers are busy. Message will be retried later");
                }
            }
        } catch (Exception ex) {
            logger.error("poolAndExecute failed " + ex.getMessage(), ex);
        }
    }

    private void execute(EventHandler.Action action, Object payload, String event, String eventId) throws Exception {
        switch (action.getAction()) {
            case find_update:
                find_update(action, payload, event, eventId);
                return;
            default:
                break;
        }
        throw new UnsupportedOperationException("Action not supported " + action.getAction());
    }

    private void find_update(EventHandler.Action action, Object payload, String event, String eventId) {
        try {

            FindUpdateAction findUpdateAction = new FindUpdateAction(executor);
            findUpdateAction.handleInternal(action, payload, event, eventId);

        } catch (Exception e) {
            logger.error("find_update: failed with " + e.getMessage() +
                            " for action=" + action + ", payload=" + payload + ", event=" + event + ", eventId=" + eventId, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EventWrapper {
        public String messageId;
        public String receiptHandle;

        public EventHandler.Action action;
        public String eventId;
        public Object payload;
        public String event;
        public int retried;

        @Override
        public String toString() {
            return "EventWrapper{" +
                    "messageId=" + messageId +
                    ", receiptHandle=" + receiptHandle +
                    ", action=" + action +
                    ", eventId='" + eventId + '\'' +
                    ", payload=" + payload +
                    ", event='" + event + '\'' +
                    ", retried=" + retried +
                    '}';
        }
    }
}
