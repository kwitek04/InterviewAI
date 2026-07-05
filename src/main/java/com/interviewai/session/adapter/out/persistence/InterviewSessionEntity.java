package com.interviewai.session.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * JPA representation of an interview session and its ordered conversation history.
 */
@Entity
@Table(name = "interview_session")
class InterviewSessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String state;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<MessageEntity> messages = new ArrayList<>();

    protected InterviewSessionEntity() {
    }

    private InterviewSessionEntity(UUID id, String state) {
        this.id = id;
        this.state = state;
    }

    static InterviewSessionEntity create(UUID id, String state) {
        return new InterviewSessionEntity(id, state);
    }

    /**
     * Appends a new message to this session's conversation history, assigning it
     * the next sequential position.
     */
    void addMessage(String role, String content, Instant createdAt) {
        MessageEntity message = MessageEntity.of(messages.size(), role, content, createdAt);
        message.assignTo(this);
        messages.add(message);
    }

    UUID getId() {
        return id;
    }

    String getState() {
        return state;
    }

    void setState(String state) {
        this.state = state;
    }

    List<MessageEntity> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    int messageCount() {
        return messages.size();
    }
}
