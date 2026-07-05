package com.interviewai.session.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Commands that can be applied to an {@link InterviewSession} to advance its state.
 */
public sealed interface SessionCommand {

    record StartInterview() implements SessionCommand {
    }

    record AskQuestion(String content, Instant askedAt) implements SessionCommand {

        public AskQuestion {
            Preconditions.requireNonBlank(content, "content");
            Objects.requireNonNull(askedAt, "askedAt must not be null");
        }
    }

    record SubmitAnswer(String content, Instant answeredAt) implements SessionCommand {

        public SubmitAnswer {
            Preconditions.requireNonBlank(content, "content");
            Objects.requireNonNull(answeredAt, "answeredAt must not be null");
        }
    }

    record EndInterview() implements SessionCommand {
    }
}
