package com.interviewai.session.application;

import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.Message;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionState;
import com.interviewai.session.domain.Transcript;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CompletedInterviewSnapshotsTest {

    private static final Instant T0 = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    @DisplayName("pairs interviewer questions with following candidate answers and skips a trailing question")
    void from_pairsAnsweredQuestionsAndSkipsTrailingUnanswered() {
        SessionId id = SessionId.generate();
        Transcript transcript = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Q0", T0))
                .append(new Message(MessageRole.CANDIDATE, "A0", T0.plusSeconds(1)))
                .append(new Message(MessageRole.INTERVIEWER, "Q1", T0.plusSeconds(2)))
                .append(new Message(MessageRole.CANDIDATE, "A1", T0.plusSeconds(3)))
                .append(new Message(MessageRole.INTERVIEWER, "Q2 unanswered", T0.plusSeconds(4)));
        InterviewSession completed = new InterviewSession(id, null, new SessionState.Completed(), transcript);

        CompletedInterviewSnapshot snapshot = CompletedInterviewSnapshots.from(completed);

        assertThat(snapshot.sessionId()).isEqualTo(id);
        assertThat(snapshot.answeredQuestions()).containsExactly(
                new CompletedInterviewSnapshot.AnsweredQuestion(0, "Q0", "A0"),
                new CompletedInterviewSnapshot.AnsweredQuestion(1, "Q1", "A1"));
    }
}
