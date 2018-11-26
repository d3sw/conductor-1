package com.netflix.conductor.core.events;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import com.netflix.conductor.core.events.queue.Message;

import java.util.List;
import java.util.stream.Collectors;

public class RetryQueuePooler {
    private AmazonSQS sqs;
    private String queueName;
    private String queueURL;
    private int visibilityTimeout;

    public RetryQueuePooler(AmazonSQS sqs, String queueName, int visibilityTimeout) {
        this.sqs = sqs;
        this.queueName = queueName;
        this.queueURL = getOrCreateQueue();
        this.visibilityTimeout = visibilityTimeout;
    }

    public void publish(List<Message> messages) {
        SendMessageBatchRequest batch = new SendMessageBatchRequest(queueURL);
        messages.forEach(msg -> {
            SendMessageBatchRequestEntry request = new SendMessageBatchRequestEntry(msg.getId(), msg.getPayload());
            batch.getEntries().add(request);
        });
        SendMessageBatchResult result = sqs.sendMessageBatch(batch);
        System.out.println("result = " + result);
    }

    public List<Message> receive(int size) {
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                .withQueueUrl(queueURL)
                .withVisibilityTimeout(visibilityTimeout)
                .withMaxNumberOfMessages(size);
        ReceiveMessageResult result = sqs.receiveMessage(receiveMessageRequest);
        return result.getMessages().stream()
                .map(msg -> new Message(msg.getMessageId(), msg.getBody(), msg.getReceiptHandle()))
                .collect(Collectors.toList());
    }

    public List<String> delete(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        DeleteMessageBatchRequest batch = new DeleteMessageBatchRequest().withQueueUrl(queueURL);
        List<DeleteMessageBatchRequestEntry> entries = batch.getEntries();

        messages.forEach(m -> entries.add(new DeleteMessageBatchRequestEntry().withId(m.getId()).withReceiptHandle(m.getReceipt())));

        DeleteMessageBatchResult result = sqs.deleteMessageBatch(batch);
        return result.getFailed().stream().map(BatchResultErrorEntry::getId).collect(Collectors.toList());

    }

    private String getOrCreateQueue() {
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

    public static void main(String[] args) {
        String accessKey = "AKIAITT4LXQVJVO77WSA";
        String secretKey = "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";
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);

        AmazonSQS sqs = AmazonSQSClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();

        RetryQueuePooler queue = new RetryQueuePooler(sqs, "retry_find_update", 5);

//        Message message = new Message();
//        message.setId(UUID.randomUUID().toString());
//        message.setPayload("foo=bar");
//
//        queue.publish(Collections.singletonList(message));

        List<Message> messages = queue.receive(6);
        System.out.println("messages = " + messages);

        queue.delete(messages);
    }
}
