package com.interviewai.session.adapter.out.messaging;

import com.interviewai.session.application.InterviewCompletedMessageMapper;
import com.interviewai.session.application.port.out.CompletedInterviewPublisher;
import com.interviewai.shared.InterviewCompletedEvent;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes interview completion events to the configured SQS queue.
 */
@Component
class SqsCompletedInterviewPublisher implements CompletedInterviewPublisher {

    private final SqsClient sqsClient;
    private final SqsMessagingProperties properties;

    SqsCompletedInterviewPublisher(SqsClient sqsClient, SqsMessagingProperties properties) {
        this.sqsClient = sqsClient;
        this.properties = properties;
    }

    @Override
    public void publish(InterviewCompletedEvent event) {
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName(properties.queueName())
                        .build())
                .queueUrl();

        Map<String, MessageAttributeValue> attributes = new LinkedHashMap<>();
        InterviewCompletedMessageMapper.toMessageAttributes(event).forEach((name, value) ->
                attributes.put(name, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(value)
                        .build()));

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(InterviewCompletedMessageMapper.toJsonBody(event))
                .messageAttributes(attributes)
                .build());
    }
}
