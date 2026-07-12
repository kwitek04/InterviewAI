package com.interviewai.interview.adapter.in.web;

import com.interviewai.interview.application.InvalidLastEventIdException;
import com.interviewai.interview.application.QuestionResponseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates interview streaming exceptions into RFC 7807 problem responses.
 */
@RestControllerAdvice(assignableTypes = InterviewStreamController.class)
class InterviewStreamExceptionHandler {

    @ExceptionHandler(QuestionResponseNotFoundException.class)
    ProblemDetail handleQuestionResponseNotFound(QuestionResponseNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidLastEventIdException.class)
    ProblemDetail handleInvalidLastEventId(InvalidLastEventIdException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
