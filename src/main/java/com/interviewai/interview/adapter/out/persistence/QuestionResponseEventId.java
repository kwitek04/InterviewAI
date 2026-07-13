package com.interviewai.interview.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class QuestionResponseEventId implements Serializable {

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(nullable = false)
    private int sequence;

    protected QuestionResponseEventId() {
    }

    private QuestionResponseEventId(UUID responseId, int sequence) {
        this.responseId = responseId;
        this.sequence = sequence;
    }

    static QuestionResponseEventId of(UUID responseId, int sequence) {
        return new QuestionResponseEventId(responseId, sequence);
    }

    UUID getResponseId() {
        return responseId;
    }

    int getSequence() {
        return sequence;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof QuestionResponseEventId that)) {
            return false;
        }
        return sequence == that.sequence && Objects.equals(responseId, that.responseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(responseId, sequence);
    }
}
