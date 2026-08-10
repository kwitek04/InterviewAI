package com.interviewai.report.adapter.in.web;

import com.interviewai.report.application.ReportFailedException;
import com.interviewai.report.application.ReportNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates report query exceptions into RFC 7807 problem responses.
 */
@RestControllerAdvice
class ReportExceptionHandler {

    @ExceptionHandler(ReportNotFoundException.class)
    ProblemDetail handleNotFound(ReportNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ReportFailedException.class)
    ProblemDetail handleFailed(ReportFailedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.safeMessage());
        problem.setTitle("Report generation failed");
        return problem;
    }
}
