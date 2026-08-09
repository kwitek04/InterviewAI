package com.interviewai.session.adapter.out.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsQueueInitializerTest {

    private static final String QUEUE_NAME = "interview-completed";
    private static final String DLQ_NAME = "interview-completed-dlq";
    private static final String DLQ_URL = "http://localhost:4566/000000000000/" + DLQ_NAME;
    private static final String QUEUE_URL = "http://localhost:4566/000000000000/" + QUEUE_NAME;
    private static final String DLQ_ARN = "arn:aws:sqs:eu-central-1:000000000000:" + DLQ_NAME;

    private static final SqsMessagingProperties PROPERTIES = new SqsMessagingProperties(
            URI.create("http://localhost:4566"),
            "eu-central-1",
            "test",
            "test",
            QUEUE_NAME,
            DLQ_NAME,
            Duration.ofSeconds(20),
            Duration.ofMinutes(2),
            5,
            Duration.ofSeconds(10),
            50);

    @Mock
    private SqsClient sqsClient;

    @Test
    @DisplayName("an existing queue with an incompatible visibility timeout fails startup")
    void afterSingletonsInstantiated_whenVisibilityTimeoutDiffers_throws() {
        stubDlqCreatedAndMainQueueExisting(Map.of(
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20",
                QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + DLQ_ARN + "\",\"maxReceiveCount\":\"5\"}"));

        assertThatThrownBy(() -> new SqsQueueInitializer(sqsClient, PROPERTIES).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(QUEUE_NAME)
                .hasMessageContaining(QueueAttributeName.VISIBILITY_TIMEOUT.toString());
    }

    @Test
    @DisplayName("an existing queue whose redrive policy points elsewhere fails startup")
    void afterSingletonsInstantiated_whenRedrivePolicyDiffers_throws() {
        stubDlqCreatedAndMainQueueExisting(Map.of(
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20",
                QueueAttributeName.VISIBILITY_TIMEOUT, "120",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"arn:aws:sqs:eu-central-1:000000000000:other-dlq\","
                        + "\"maxReceiveCount\":\"5\"}"));

        assertThatThrownBy(() -> new SqsQueueInitializer(sqsClient, PROPERTIES).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(QueueAttributeName.REDRIVE_POLICY.toString());
    }

    @Test
    @DisplayName("an existing queue with matching attributes is accepted")
    void afterSingletonsInstantiated_whenAttributesMatch_succeeds() {
        stubDlqCreatedAndMainQueueExisting(Map.of(
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20",
                QueueAttributeName.VISIBILITY_TIMEOUT, "120",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + DLQ_ARN + "\",\"maxReceiveCount\":\"5\"}"));

        assertThatCode(() -> new SqsQueueInitializer(sqsClient, PROPERTIES).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unreachable SQS does not prevent the application from starting")
    void afterSingletonsInstantiated_whenSqsUnreachable_doesNotThrow() {
        when(sqsClient.createQueue(any(CreateQueueRequest.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThatCode(() -> new SqsQueueInitializer(sqsClient, PROPERTIES).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    private void stubDlqCreatedAndMainQueueExisting(Map<QueueAttributeName, String> existingMainQueueAttributes) {
        when(sqsClient.createQueue(any(CreateQueueRequest.class))).thenAnswer(invocation -> {
            CreateQueueRequest request = invocation.getArgument(0);
            if (DLQ_NAME.equals(request.queueName())) {
                return CreateQueueResponse.builder().queueUrl(DLQ_URL).build();
            }
            throw QueueNameExistsException.builder().message("queue exists").build();
        });
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl(QUEUE_URL).build());
        when(sqsClient.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenAnswer(invocation -> {
            GetQueueAttributesRequest request = invocation.getArgument(0);
            if (request.attributeNames().contains(QueueAttributeName.QUEUE_ARN)) {
                return GetQueueAttributesResponse.builder()
                        .attributes(Map.of(QueueAttributeName.QUEUE_ARN, DLQ_ARN))
                        .build();
            }
            return GetQueueAttributesResponse.builder()
                    .attributes(existingMainQueueAttributes)
                    .build();
        });
    }
}
