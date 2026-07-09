package com.interviewai.session.adapter.in.web;

import java.util.UUID;

/**
 * Optional payload for starting a session tied to a specific CV.
 */
record StartSessionRequest(UUID cvId) {
}
