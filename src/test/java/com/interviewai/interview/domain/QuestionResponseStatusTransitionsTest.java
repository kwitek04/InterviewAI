package com.interviewai.interview.domain;

import com.interviewai.interview.application.InvalidQuestionResponseStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionResponseStatusTransitionsTest {

    @Test
    @DisplayName("pending can transition to streaming")
    void pending_toStreaming_succeeds() {
        assertThat(QuestionResponseStatuses.toStreaming(QuestionResponseStatus.PENDING))
                .isEqualTo(QuestionResponseStatus.STREAMING);
    }

    @Test
    @DisplayName("streaming can transition to completed")
    void streaming_toCompleted_succeeds() {
        assertThat(QuestionResponseStatuses.toCompleted(QuestionResponseStatus.STREAMING))
                .isEqualTo(QuestionResponseStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionResponseStatus.class, names = {"PENDING", "STREAMING"})
    @DisplayName("active statuses can transition to failed")
    void active_toFailed_succeeds(QuestionResponseStatus status) {
        assertThat(QuestionResponseStatuses.toFailed(status))
                .isEqualTo(QuestionResponseStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionResponseStatus.class, names = {"PENDING", "STREAMING"})
    @DisplayName("active statuses can transition to cancelled")
    void active_toCancelled_succeeds(QuestionResponseStatus status) {
        assertThat(QuestionResponseStatuses.toCancelled(status))
                .isEqualTo(QuestionResponseStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionResponseStatus.class, names = {"COMPLETED", "FAILED", "CANCELLED"})
    @DisplayName("terminal statuses cannot transition again")
    void terminalStatuses_rejectFurtherTransitions(QuestionResponseStatus terminalStatus) {
        assertThatThrownBy(() -> QuestionResponseStatuses.toStreaming(terminalStatus))
                .isInstanceOf(InvalidQuestionResponseStatusTransitionException.class);
        assertThatThrownBy(() -> QuestionResponseStatuses.toCompleted(terminalStatus))
                .isInstanceOf(InvalidQuestionResponseStatusTransitionException.class);
        assertThatThrownBy(() -> QuestionResponseStatuses.toFailed(terminalStatus))
                .isInstanceOf(InvalidQuestionResponseStatusTransitionException.class);
        assertThatThrownBy(() -> QuestionResponseStatuses.toCancelled(terminalStatus))
                .isInstanceOf(InvalidQuestionResponseStatusTransitionException.class);
    }

    @Test
    @DisplayName("pending cannot transition directly to completed")
    void pending_toCompleted_isRejected() {
        assertThatThrownBy(() -> QuestionResponseStatuses.toCompleted(QuestionResponseStatus.PENDING))
                .isInstanceOf(InvalidQuestionResponseStatusTransitionException.class);
    }
}
