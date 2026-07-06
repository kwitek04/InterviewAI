package com.interviewai.session.adapter.in.web;

import com.interviewai.session.domain.Message;

import java.time.Instant;

/**
 * A single transcript turn, as exposed over the REST API.
 */
record MessageView(String role, String content, Instant timestamp) {

    static MessageView from(Message message) {
        return new MessageView(message.role().name(), message.content(), message.timestamp());
    }
}
