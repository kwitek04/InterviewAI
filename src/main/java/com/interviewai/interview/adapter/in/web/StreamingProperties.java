package com.interviewai.interview.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for SSE response streaming.
 */
@ConfigurationProperties(prefix = "interviewai.streaming")
record StreamingProperties(
        Duration emitterTimeout,
        Duration pollInterval,
        Duration heartbeatInterval) {

    StreamingProperties {
        if (emitterTimeout == null) {
            emitterTimeout = Duration.ofMinutes(5);
        }
        if (pollInterval == null) {
            pollInterval = Duration.ofMillis(250);
        }
        if (heartbeatInterval == null) {
            heartbeatInterval = Duration.ofSeconds(15);
        }
    }
}
