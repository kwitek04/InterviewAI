package com.interviewai.report.application;

import com.interviewai.report.application.QuestionAssessmentValidator.ScoredAnswer;
import com.interviewai.report.application.port.out.ProcessedEventStore;
import com.interviewai.report.application.port.out.ReportGenerator;
import com.interviewai.report.application.port.out.ReportRepository;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.report.domain.ReportStatus;
import com.interviewai.session.application.CompletedInterviewSnapshot;
import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionState;
import com.interviewai.session.domain.Transcript;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final SessionId SESSION_ID = SessionId.generate();
    private static final String RAW_PROVIDER_DETAIL = "verbatim-model-output-that-must-not-be-persisted";

    @Mock
    private SessionApplicationService sessionApplicationService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportGenerator reportGenerator;

    @Mock
    private ProcessedEventStore processedEventStore;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ReportApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReportApplicationService(
                sessionApplicationService,
                reportRepository,
                reportGenerator,
                processedEventStore,
                new ReportGenerationProperties(3),
                transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("successful generation persists READY and marks the session report-ready")
    void generateReport_happyPath_marksReady() {
        CompletedInterviewSnapshot snapshot = snapshot();
        when(sessionApplicationService.requireCompletedInterviewSnapshot(SESSION_ID)).thenReturn(snapshot);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(InterviewReport.pending(id, SESSION_ID, NOW).markGenerating(NOW));
        });
        when(reportGenerator.evaluateAnswers(snapshot)).thenReturn(List.of(
                new ScoredAnswer(0, 4, "Solid"),
                new ScoredAnswer(1, 3, "Ok")));
        when(reportGenerator.synthesize(eq(snapshot), any())).thenReturn(SynthesisResult.validated(
                List.of("Clear communicator"),
                List.of("Needs more depth"),
                List.of("Prepare examples")));
        when(sessionApplicationService.markReportReady(SESSION_ID)).thenReturn(
                new InterviewSession(SESSION_ID, null, new SessionState.ReportReady(), Transcript.empty()));

        InterviewReport result = service.generateReport(SESSION_ID);

        assertThat(result.status()).isEqualTo(ReportStatus.READY);
        assertThat(result.assessments()).hasSize(2);
        verify(sessionApplicationService).markReportReady(SESSION_ID);
        verify(reportGenerator).synthesize(eq(snapshot), any());
        verify(processedEventStore, never()).markCompleted(any(), any());
    }

    @Test
    @DisplayName("successful generation with a claim marks the claim completed in the ready transaction")
    void generateReport_withClaim_marksProcessedEventCompleted() {
        CompletedInterviewSnapshot snapshot = snapshot();
        UUID eventId = UUID.randomUUID();
        when(sessionApplicationService.requireCompletedInterviewSnapshot(SESSION_ID)).thenReturn(snapshot);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(InterviewReport.pending(id, SESSION_ID, NOW).markGenerating(NOW));
        });
        when(reportGenerator.evaluateAnswers(snapshot)).thenReturn(List.of(
                new ScoredAnswer(0, 4, "Solid"),
                new ScoredAnswer(1, 3, "Ok")));
        when(reportGenerator.synthesize(eq(snapshot), any())).thenReturn(SynthesisResult.validated(
                List.of("Clear communicator"),
                List.of("Needs more depth"),
                List.of("Prepare examples")));
        when(sessionApplicationService.markReportReady(SESSION_ID)).thenReturn(
                new InterviewSession(SESSION_ID, null, new SessionState.ReportReady(), Transcript.empty()));

        InterviewReport result = service.generateReport(SESSION_ID, eventId, "worker-1");

        assertThat(result.status()).isEqualTo(ReportStatus.READY);
        verify(processedEventStore).markCompleted(eventId, "worker-1");
    }

    @Test
    @DisplayName("stage 2 is not called when stage 1 validation fails after retries")
    void generateReport_whenStage1Invalid_doesNotCallStage2() {
        CompletedInterviewSnapshot snapshot = snapshot();
        when(sessionApplicationService.requireCompletedInterviewSnapshot(SESSION_ID)).thenReturn(snapshot);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(InterviewReport.pending(id, SESSION_ID, NOW).markGenerating(NOW));
        });
        when(reportGenerator.evaluateAnswers(snapshot)).thenReturn(List.of(new ScoredAnswer(0, 4, "only one")));

        assertThatThrownBy(() -> service.generateReport(SESSION_ID))
                .isInstanceOf(ReportGenerationException.class);
        verify(reportGenerator, never()).synthesize(any(), any());
        verify(sessionApplicationService, never()).markReportReady(any());
    }

    @Test
    @DisplayName("transient stage failures are retried only up to the configured bound")
    void generateReport_retriesTransientFailuresUpToBound() {
        CompletedInterviewSnapshot snapshot = snapshot();
        when(sessionApplicationService.requireCompletedInterviewSnapshot(SESSION_ID)).thenReturn(snapshot);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(InterviewReport.pending(id, SESSION_ID, NOW).markGenerating(NOW));
        });

        AtomicInteger attempts = new AtomicInteger();
        when(reportGenerator.evaluateAnswers(snapshot)).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return List.of(new ScoredAnswer(0, 4, "Solid"), new ScoredAnswer(1, 3, "Ok"));
        });
        when(reportGenerator.synthesize(eq(snapshot), any())).thenReturn(SynthesisResult.validated(
                List.of("Clear communicator"),
                List.of("Needs more depth"),
                List.of("Prepare examples")));
        when(sessionApplicationService.markReportReady(SESSION_ID)).thenReturn(
                new InterviewSession(SESSION_ID, null, new SessionState.ReportReady(), Transcript.empty()));

        InterviewReport result = service.generateReport(SESSION_ID);

        assertThat(result.status()).isEqualTo(ReportStatus.READY);
        verify(reportGenerator, times(3)).evaluateAnswers(snapshot);
    }

    @Test
    @DisplayName("exhausted retries mark the report FAILED")
    void generateReport_whenRetriesExhausted_marksFailed() {
        CompletedInterviewSnapshot snapshot = snapshot();
        when(sessionApplicationService.requireCompletedInterviewSnapshot(SESSION_ID)).thenReturn(snapshot);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(InterviewReport.pending(id, SESSION_ID, NOW).markGenerating(NOW));
        });
        when(reportGenerator.evaluateAnswers(snapshot)).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.generateReport(SESSION_ID))
                .isInstanceOf(ReportGenerationException.class);

        ArgumentCaptor<InterviewReport> saved = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getLast().status()).isEqualTo(ReportStatus.FAILED);
        verify(reportGenerator, times(3)).evaluateAnswers(snapshot);
        verify(reportGenerator, never()).synthesize(any(), any());
    }

    @Test
    @DisplayName("an already READY report is returned without regenerating")
    void generateReport_whenAlreadyReady_returnsExisting() {
        InterviewReport ready = InterviewReport.pending(UUID.randomUUID(), SESSION_ID, NOW)
                .markGenerating(NOW)
                .markReady(
                        List.of(new QuestionAssessment(0, "Q0", "A0", 4, "Good")),
                        List.of("s"),
                        List.of("w"),
                        List.of("r"),
                        NOW);
        when(sessionApplicationService.requireCompletedInterviewSnapshot(SESSION_ID)).thenReturn(snapshot());
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(ready));

        InterviewReport result = service.generateReport(SESSION_ID);

        assertThat(result).isSameAs(ready);
        verify(reportGenerator, never()).evaluateAnswers(any());
        verify(sessionApplicationService).markReportReady(SESSION_ID);
    }

    @Test
    @DisplayName("exhausted stage 2 retries mark the report FAILED and leave the session untouched")
    void generateReport_whenStage2RetriesExhausted_marksFailed() {
        CompletedInterviewSnapshot snapshot = snapshot();
        stubPendingReportFor(snapshot);
        when(reportGenerator.evaluateAnswers(snapshot)).thenReturn(List.of(
                new ScoredAnswer(0, 4, "Solid"),
                new ScoredAnswer(1, 3, "Ok")));
        when(reportGenerator.synthesize(eq(snapshot), any()))
                .thenThrow(new IllegalStateException("ollama returned: " + RAW_PROVIDER_DETAIL));

        assertThatThrownBy(() -> service.generateReport(SESSION_ID))
                .isInstanceOf(ReportGenerationException.class);

        verify(reportGenerator, times(3)).synthesize(eq(snapshot), any());
        verify(sessionApplicationService, never()).markReportReady(any());

        ArgumentCaptor<InterviewReport> saved = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportRepository, times(2)).save(saved.capture());
        InterviewReport failed = saved.getAllValues().getLast();
        assertThat(failed.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(failed.failureMessage()).isEqualTo(ReportFailureMessages.GENERATION_FAILED);
        assertThat(failed.failureMessage()).doesNotContain(RAW_PROVIDER_DETAIL);
    }

    @Test
    @DisplayName("a transient stage 2 failure is retried and still produces a READY report")
    void generateReport_whenStage2RecoversWithinBound_marksReady() {
        CompletedInterviewSnapshot snapshot = snapshot();
        stubPendingReportFor(snapshot);
        when(reportGenerator.evaluateAnswers(snapshot)).thenReturn(List.of(
                new ScoredAnswer(0, 4, "Solid"),
                new ScoredAnswer(1, 3, "Ok")));

        AtomicInteger attempts = new AtomicInteger();
        when(reportGenerator.synthesize(eq(snapshot), any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("transient");
            }
            return SynthesisResult.validated(
                    List.of("Clear communicator"),
                    List.of("Needs more depth"),
                    List.of("Prepare examples"));
        });
        when(sessionApplicationService.markReportReady(SESSION_ID)).thenReturn(
                new InterviewSession(SESSION_ID, null, new SessionState.ReportReady(), Transcript.empty()));

        InterviewReport result = service.generateReport(SESSION_ID);

        assertThat(result.status()).isEqualTo(ReportStatus.READY);
        verify(reportGenerator, times(2)).synthesize(eq(snapshot), any());
        verify(reportGenerator, times(1)).evaluateAnswers(snapshot);
    }

    @Test
    @DisplayName("invalid generated content is stored as a validation failure, not as provider output")
    void generateReport_whenContentInvalid_storesSanitizedValidationMessage() {
        CompletedInterviewSnapshot snapshot = snapshot();
        stubPendingReportFor(snapshot);
        when(reportGenerator.evaluateAnswers(snapshot))
                .thenThrow(new InvalidReportContentException("model returned: " + RAW_PROVIDER_DETAIL));

        assertThatThrownBy(() -> service.generateReport(SESSION_ID))
                .isInstanceOf(ReportGenerationException.class);

        ArgumentCaptor<InterviewReport> saved = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportRepository, times(2)).save(saved.capture());
        InterviewReport failed = saved.getAllValues().getLast();
        assertThat(failed.failureMessage()).isEqualTo(ReportFailureMessages.INVALID_CONTENT);
        assertThat(failed.failureMessage()).doesNotContain(RAW_PROVIDER_DETAIL);
    }

    private void stubPendingReportFor(CompletedInterviewSnapshot snapshot) {
        when(sessionApplicationService.requireCompletedInterviewSnapshot(SESSION_ID)).thenReturn(snapshot);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportRepository.findById(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(InterviewReport.pending(id, SESSION_ID, NOW).markGenerating(NOW));
        });
    }

    private static CompletedInterviewSnapshot snapshot() {
        return new CompletedInterviewSnapshot(
                SESSION_ID,
                List.of(
                        new CompletedInterviewSnapshot.AnsweredQuestion(0, "Q0", "A0"),
                        new CompletedInterviewSnapshot.AnsweredQuestion(1, "Q1", "A1")));
    }
}
