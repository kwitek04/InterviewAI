package com.interviewai.session.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A single turn in an interview conversation.
 */
public record Message(MessageRole role, String content, Instant timestamp) {

    public Message {
        Objects.requireNonNull(role, "role must not be null");
        Preconditions.requireNonBlank(content, "content");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
