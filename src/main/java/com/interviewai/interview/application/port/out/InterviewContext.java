package com.interviewai.interview.application.port.out;

import java.util.List;

/**
 * Retrieval context used by question generators to ground questions in the CV
 * and job offer.
 */
public record InterviewContext(String jobOffer, List<String> cvExcerpts) {

    public InterviewContext {
        cvExcerpts = cvExcerpts == null ? List.of() : List.copyOf(cvExcerpts);
    }

    public static InterviewContext empty() {
        return new InterviewContext(null, List.of());
    }
}
