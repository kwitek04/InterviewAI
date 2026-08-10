package com.interviewai.report.adapter.in.web;

import com.interviewai.report.application.ReportQueryService;
import com.interviewai.report.application.ReportView;
import com.interviewai.shared.SessionId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only REST API for interview report status and results.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/report")
class ReportController {

    private final ReportQueryService reportQueryService;

    ReportController(ReportQueryService reportQueryService) {
        this.reportQueryService = reportQueryService;
    }

    /**
     * Returns report progress ({@code 202}) or the finished report ({@code 200}).
     */
    @GetMapping
    ResponseEntity<?> getReport(@PathVariable UUID sessionId) {
        ReportView view = reportQueryService.getReport(new SessionId(sessionId));
        return switch (view) {
            case ReportView.InProgress inProgress -> ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(new ReportStatusResponse(inProgress.status()));
            case ReportView.Ready ready -> ResponseEntity.ok(ReportResponse.from(ready.report()));
        };
    }
}
