package com.interviewai.interview.adapter.out.persistence;

import com.interviewai.interview.domain.QuestionResponseEventType;
import com.interviewai.interview.domain.QuestionResponseStatus;

final class QuestionResponseStatusMapper {

    private QuestionResponseStatusMapper() {
    }

    static String toStorage(QuestionResponseStatus status) {
        return status.name();
    }

    static QuestionResponseStatus toDomain(String status) {
        return QuestionResponseStatus.valueOf(status);
    }

    static String toStorage(QuestionResponseEventType eventType) {
        return eventType.name();
    }

    static QuestionResponseEventType toEventDomain(String eventType) {
        return QuestionResponseEventType.valueOf(eventType);
    }
}
