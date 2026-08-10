package com.interviewai.session.application;

import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.Message;
import com.interviewai.session.domain.MessageRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link CompletedInterviewSnapshot} values from interview transcripts.
 */
final class CompletedInterviewSnapshots {

    private CompletedInterviewSnapshots() {
    }

    static CompletedInterviewSnapshot from(InterviewSession session) {
        List<CompletedInterviewSnapshot.AnsweredQuestion> pairs = new ArrayList<>();
        List<Message> messages = session.transcript().messages();
        int index = 0;
        for (int i = 0; i + 1 < messages.size(); i++) {
            Message current = messages.get(i);
            Message next = messages.get(i + 1);
            if (current.role() == MessageRole.INTERVIEWER && next.role() == MessageRole.CANDIDATE) {
                pairs.add(new CompletedInterviewSnapshot.AnsweredQuestion(
                        index++,
                        current.content(),
                        next.content()));
                i++;
            }
        }
        return new CompletedInterviewSnapshot(session.id(), pairs);
    }
}
