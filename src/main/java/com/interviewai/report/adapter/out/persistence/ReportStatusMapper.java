package com.interviewai.report.adapter.out.persistence;

import com.interviewai.report.domain.ReportStatus;

final class ReportStatusMapper {

    private ReportStatusMapper() {
    }

    static String toStorage(ReportStatus status) {
        return status.name();
    }

    static ReportStatus fromStorage(String status) {
        return ReportStatus.valueOf(status);
    }
}
