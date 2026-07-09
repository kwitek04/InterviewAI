package com.interviewai.cv.application;

import com.interviewai.shared.CvId;

/**
 * Thrown when no CV document exists for a given {@link CvId}.
 */
public final class CvNotFoundException extends RuntimeException {

    private final CvId cvId;

    public CvNotFoundException(CvId cvId) {
        super("No CV document found with id " + cvId.value());
        this.cvId = cvId;
    }

    public CvId cvId() {
        return cvId;
    }
}
