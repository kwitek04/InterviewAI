package com.interviewai.report.domain;

/**
 * Pure transition rules for {@link ReportStatus}.
 */
public final class ReportStatuses {

    private ReportStatuses() {
    }

    public static ReportStatus toGenerating(ReportStatus current) {
        return switch (current) {
            case PENDING, FAILED, GENERATING -> ReportStatus.GENERATING;
            case READY -> throw new ReportTransitionException(current, ReportStatus.GENERATING);
        };
    }

    public static ReportStatus toReady(ReportStatus current) {
        return switch (current) {
            case GENERATING -> ReportStatus.READY;
            case PENDING, READY, FAILED -> throw new ReportTransitionException(current, ReportStatus.READY);
        };
    }

    public static ReportStatus toFailed(ReportStatus current) {
        return switch (current) {
            case PENDING, GENERATING -> ReportStatus.FAILED;
            case READY, FAILED -> throw new ReportTransitionException(current, ReportStatus.FAILED);
        };
    }
}
