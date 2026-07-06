package com.interviewai.session.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * The candidate's answer to the interviewer's last question.
 */
record SubmitAnswerRequest(@NotBlank String answer) {
}
