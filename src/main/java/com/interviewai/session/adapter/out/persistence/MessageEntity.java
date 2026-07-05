package com.interviewai.session.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA representation of a single turn in an interview conversation.
 */
@Entity
@Table(name = "session_message")
class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSessionEntity session;

    @Column(name = "message_position", nullable = false)
    private int position;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    private MessageEntity(int position, String role, String content, Instant createdAt) {
        this.position = position;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    static MessageEntity of(int position, String role, String content, Instant createdAt) {
        return new MessageEntity(position, role, content, createdAt);
    }

    void assignTo(InterviewSessionEntity session) {
        this.session = session;
    }

    int getPosition() {
        return position;
    }

    String getRole() {
        return role;
    }

    String getContent() {
        return content;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
