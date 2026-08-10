package com.interviewai.session.adapter.out.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;

import java.util.Map;

/**
 * Ensures the interview-completed queue and its DLQ exist with the configured
 * visibility timeout, long polling, and redrive policy.
 */
class SqsQueueInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SqsQueueInitializer.class);

    private final SqsClient sqsClient;
    private final SqsMessagingProperties properties;

    SqsQueueInitializer(SqsClient sqsClient, SqsMessagingProperties properties) {
        this.sqsClient = sqsClient;
        this.properties = properties;
    }

    /**
     * Unreachable SQS is tolerated so the application can start without messaging infrastructure,
     * but an existing queue with incompatible attributes is a misconfiguration and fails startup.
     */
    @Override
    public void afterSingletonsInstantiated() {
        try {
            ensureQueuesExist();
        } catch (SdkException exception) {
            log.warn("Could not reach SQS to verify or create queues '{}/{}'; "
                            + "interview completion publishing will fail until SQS is reachable.",
                    properties.queueName(), properties.dlqName(), exception);
        }
    }

    private void ensureQueuesExist() {
        String dlqUrl = ensureQueue(properties.dlqName(), Map.of(
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
                Long.toString(properties.longPollDuration().toSeconds()),
                QueueAttributeName.VISIBILITY_TIMEOUT,
                Long.toString(properties.visibilityTimeout().toSeconds())));

        String dlqArn = queueArn(dlqUrl);
        String redrivePolicy = """
                {"deadLetterTargetArn":"%s","maxReceiveCount":"%d"}
                """.formatted(dlqArn, properties.maxReceiveCount()).trim();

        ensureQueue(properties.queueName(), Map.of(
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
                Long.toString(properties.longPollDuration().toSeconds()),
                QueueAttributeName.VISIBILITY_TIMEOUT,
                Long.toString(properties.visibilityTimeout().toSeconds()),
                QueueAttributeName.REDRIVE_POLICY,
                redrivePolicy));
    }

    private String ensureQueue(String queueName, Map<QueueAttributeName, String> attributes) {
        try {
            return sqsClient.createQueue(CreateQueueRequest.builder()
                            .queueName(queueName)
                            .attributes(attributes)
                            .build())
                    .queueUrl();
        } catch (QueueNameExistsException exception) {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                    .queueUrl();
            validateExistingAttributes(queueName, queueUrl, attributes);
            return queueUrl;
        }
    }

    private void validateExistingAttributes(
            String queueName, String queueUrl, Map<QueueAttributeName, String> expected) {
        Map<QueueAttributeName, String> actual = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(expected.keySet())
                        .build())
                .attributes();

        for (Map.Entry<QueueAttributeName, String> entry : expected.entrySet()) {
            String actualValue = actual.get(entry.getKey());
            if (!attributesCompatible(entry.getKey(), entry.getValue(), actualValue)) {
                throw new IllegalStateException(
                        "SQS queue '%s' exists with incompatible %s. expected=%s actual=%s"
                                .formatted(queueName, entry.getKey(), entry.getValue(), actualValue));
            }
        }
    }

    private static boolean attributesCompatible(
            QueueAttributeName name, String expected, String actual) {
        if (actual == null) {
            return false;
        }
        if (name == QueueAttributeName.REDRIVE_POLICY) {
            return redrivePoliciesCompatible(expected, actual);
        }
        return expected.equals(actual);
    }

    private static boolean redrivePoliciesCompatible(String expected, String actual) {
        String normalizedExpected = expected.replaceAll("\\s+", "");
        String normalizedActual = actual.replaceAll("\\s+", "");
        return normalizedActual.contains("\"deadLetterTargetArn\"")
                && normalizedActual.contains(extractJsonString(normalizedExpected, "deadLetterTargetArn"))
                && normalizedActual.contains("\"maxReceiveCount\"")
                && extractJsonNumber(normalizedActual, "maxReceiveCount")
                        .equals(extractJsonNumber(normalizedExpected, "maxReceiveCount"));
    }

    private static String extractJsonString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        return valueEnd < 0 ? "" : json.substring(valueStart, valueEnd);
    }

    private static String extractJsonNumber(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        if (valueStart < json.length() && json.charAt(valueStart) == '"') {
            valueStart++;
            int valueEnd = json.indexOf('"', valueStart);
            return valueEnd < 0 ? "" : json.substring(valueStart, valueEnd);
        }
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        return json.substring(valueStart, valueEnd);
    }

    private String queueArn(String queueUrl) {
        try {
            return sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames(QueueAttributeName.QUEUE_ARN)
                            .build())
                    .attributes()
                    .get(QueueAttributeName.QUEUE_ARN);
        } catch (QueueDoesNotExistException exception) {
            throw new IllegalStateException("SQS queue URL does not exist: " + queueUrl, exception);
        }
    }
}
