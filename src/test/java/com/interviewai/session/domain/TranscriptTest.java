package com.interviewai.session.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    @DisplayName("the message list exposed by a transcript cannot be mutated")
    void messages_returnedList_isImmutable() {
        Transcript transcript = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", NOW));

        assertThatThrownBy(() -> transcript.messages()
                .add(new Message(MessageRole.CANDIDATE, "I am a developer", NOW)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("appending a message returns a new transcript and leaves the original untouched")
    void append_returnsNewTranscript_leavesOriginalUnchanged() {
        Transcript original = Transcript.empty();

        Transcript withMessage = original.append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", NOW));

        assertThat(original.messages()).isEmpty();
        assertThat(withMessage.messages()).hasSize(1);
        assertThat(withMessage).isNotSameAs(original);
    }

    @Test
    @DisplayName("a transcript built from a mutable list is not affected by later changes to that list")
    void constructor_copiesInputList_isNotAffectedByLaterMutation() {
        List<Message> mutableMessages = new ArrayList<>();
        mutableMessages.add(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", NOW));
        Transcript transcript = new Transcript(mutableMessages);

        mutableMessages.add(new Message(MessageRole.CANDIDATE, "I am a developer", NOW));

        assertThat(transcript.messages()).hasSize(1);
    }

    @Test
    @DisplayName("empty always returns a transcript with no messages")
    void empty_hasNoMessages() {
        assertThat(Transcript.empty().messages()).isEmpty();
    }
}
