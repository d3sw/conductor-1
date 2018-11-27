package com.netflix.conductor.core.events;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import com.netflix.conductor.core.events.queue.Message;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class RetryQueueFacade {
    private AmazonSQS sqs;

    @Inject
    public RetryQueueFacade(AmazonSQS sqs) {
        this.sqs = sqs;
    }

    public void publish(String queueName, List<Message> messages, int delaySeconds) {
        String queueURL = fetchQueueUrls(queueName);
        SendMessageBatchRequest batch = new SendMessageBatchRequest(queueURL);
        messages.forEach(msg -> {
            SendMessageBatchRequestEntry request = new SendMessageBatchRequestEntry();
            request.setId(msg.getId());
            request.setMessageBody(msg.getPayload());
            request.setDelaySeconds(delaySeconds);
            batch.getEntries().add(request);
        });
        sqs.sendMessageBatch(batch);
    }

    public List<Message> receive(String queueName, int size, int visibilityTimeout) {
        String queueURL = fetchQueueUrls(queueName);
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest()
                .withQueueUrl(queueURL)
                .withVisibilityTimeout(visibilityTimeout)
                .withMaxNumberOfMessages(size);
        ReceiveMessageResult result = sqs.receiveMessage(receiveMessageRequest);
        return result.getMessages().stream()
                .map(msg -> new Message(msg.getMessageId(), msg.getBody(), msg.getReceiptHandle()))
                .collect(Collectors.toList());
    }

    public List<String> delete(String queueName, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        String queueURL = fetchQueueUrls(queueName);

        DeleteMessageBatchRequest batch = new DeleteMessageBatchRequest().withQueueUrl(queueURL);
        List<DeleteMessageBatchRequestEntry> entries = batch.getEntries();

        messages.forEach(m -> entries.add(new DeleteMessageBatchRequestEntry().withId(m.getId()).withReceiptHandle(m.getReceipt())));

        DeleteMessageBatchResult result = sqs.deleteMessageBatch(batch);
        return result.getFailed().stream().map(BatchResultErrorEntry::getId).collect(Collectors.toList());
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

    public static void main(String[] args) {
        String accessKey = "AKIAITT4LXQVJVO77WSA";
        String secretKey = "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";
        BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);

        AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .build();

        RetryQueueFacade queue = new RetryQueueFacade(sqs);

//        List<Message> messages = new ArrayList<>(2);
//        for (int i = 0; i < 2; i++) {
//            Message message = new Message();
//            message.setId(UUID.randomUUID().toString());
//            message.setPayload("foo=bar" + i);
//
//            messages.add(message);
//        }
//        queue.publish(messages, 30);

        List<Message> received = queue.receive("retry_find_update", 2, 30);
        System.out.println("received = " + received);
//
//        List<String> deleted = queue.delete(received);
//        System.out.println("delete failed = " + deleted);
    }
}
