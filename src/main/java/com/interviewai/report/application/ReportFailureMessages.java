package com.interviewai.report.application;

/**
 * Produces the failure text stored on a report. Only the failure category is kept, because
 * the stored message is shown to the candidate and must never leak prompts, provider
 * responses, or stack traces.
 */
final class ReportFailureMessages {

    static final String INVALID_CONTENT = "The generated report did not pass content validation.";
    static final String GENERATION_FAILED =
            "Report generation did not complete within the allowed number of attempts.";
    static final String UNEXPECTED_FAILURE = "Report generation failed due to an internal error.";

    private static final int MAX_CAUSE_DEPTH = 10;

    private ReportFailureMessages() {
    }

    static String of(RuntimeException failure) {
        if (causedByInvalidContent(failure)) {
            return INVALID_CONTENT;
        }
        return failure instanceof ReportGenerationException ? GENERATION_FAILED : UNEXPECTED_FAILURE;
    }

    private static boolean causedByInvalidContent(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof InvalidReportContentException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
