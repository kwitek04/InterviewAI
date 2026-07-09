package com.interviewai.cv.adapter.in.web;

import com.interviewai.cv.application.CvNotFoundException;
import com.interviewai.cv.application.CvParsingException;
import com.interviewai.cv.application.CvEmbeddingException;
import com.interviewai.cv.application.InvalidCvUploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates CV-related exceptions into RFC 7807 problem responses.
 */
@RestControllerAdvice
class CvExceptionHandler {

    @ExceptionHandler(CvNotFoundException.class)
    ProblemDetail handleCvNotFound(CvNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidCvUploadException.class)
    ProblemDetail handleInvalidUpload(InvalidCvUploadException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(CvParsingException.class)
    ProblemDetail handleParsingFailure(CvParsingException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(CvEmbeddingException.class)
    ProblemDetail handleEmbeddingFailure(CvEmbeddingException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
}
