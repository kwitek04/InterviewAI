package com.interviewai.session.adapter.in.web;

import com.interviewai.cv.application.CvNotFoundException;
import com.interviewai.session.application.SessionNotFoundException;
import com.interviewai.session.domain.SessionTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Translates session-related exceptions into RFC 7807 problem responses.
 */
@RestControllerAdvice
class SessionExceptionHandler {

    @ExceptionHandler(CvNotFoundException.class)
    ProblemDetail handleCvNotFound(CvNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail handleSessionNotFound(SessionNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(SessionTransitionException.class)
    ProblemDetail handleSessionTransition(SessionTransitionException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
