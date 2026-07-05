package com.interviewai.session.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An ordered, immutable history of {@link Message} turns exchanged during an interview.
 */
public record Transcript(List<Message> messages) {

    private static final Transcript EMPTY = new Transcript(List.of());

    public Transcript {
        messages = List.copyOf(messages);
    }

    public static Transcript empty() {
        return EMPTY;
    }

    public Transcript append(Message message) {
        Objects.requireNonNull(message, "message must not be null");
        List<Message> appended = new ArrayList<>(messages);
        appended.add(message);
        return new Transcript(appended);
    }
}
