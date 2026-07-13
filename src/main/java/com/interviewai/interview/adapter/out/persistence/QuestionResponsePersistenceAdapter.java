package com.interviewai.interview.adapter.out.persistence;

import com.interviewai.interview.application.ActiveQuestionResponseAlreadyExistsException;
import com.interviewai.interview.application.InvalidQuestionResponseStatusTransitionException;
import com.interviewai.interview.application.QuestionResponseNotFoundException;
import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseEventType;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.interview.domain.QuestionResponseStatuses;
import com.interviewai.interview.domain.QuestionResponseStreamEvent;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
class QuestionResponsePersistenceAdapter implements QuestionResponseStore {

    private final QuestionResponseJpaRepository responseRepository;
    private final QuestionResponseEventJpaRepository eventRepository;
    private final Clock clock;

    QuestionResponsePersistenceAdapter(
            QuestionResponseJpaRepository responseRepository,
            QuestionResponseEventJpaRepository eventRepository,
            Clock clock) {
        this.responseRepository = responseRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public QuestionResponse createPending(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (responseRepository.findActiveBySessionId(sessionId.value()).isPresent()) {
            throw new ActiveQuestionResponseAlreadyExistsException(sessionId);
        }

        java.time.Instant now = clock.instant();
        ResponseId responseId = ResponseId.generate();
        QuestionResponseEntity entity = QuestionResponseEntity.createPending(
                responseId.value(),
                sessionId.value(),
                now);

        try {
            return toDomain(responseRepository.save(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new ActiveQuestionResponseAlreadyExistsException(sessionId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuestionResponse> findById(ResponseId responseId) {
        Objects.requireNonNull(responseId, "responseId must not be null");
        return responseRepository.findById(responseId.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuestionResponse> findActiveBySessionId(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return responseRepository.findActiveBySessionId(sessionId.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionResponse requireOwnedBySession(ResponseId responseId, SessionId sessionId) {
        Objects.requireNonNull(responseId, "responseId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        QuestionResponse response = responseRepository.findById(responseId.value())
                .map(this::toDomain)
                .orElseThrow(() -> new QuestionResponseNotFoundException(responseId));

        if (!response.sessionId().equals(sessionId)) {
            throw new QuestionResponseNotFoundException(responseId);
        }
        return response;
    }

    @Override
    @Transactional
    public void markStreaming(ResponseId responseId) {
        QuestionResponseEntity entity = loadForUpdate(responseId);
        QuestionResponseStatus nextStatus = QuestionResponseStatuses.toStreaming(
                QuestionResponseStatusMapper.toDomain(entity.getStatus()));
        entity.setStatus(QuestionResponseStatusMapper.toStorage(nextStatus));
        entity.setUpdatedAt(clock.instant());
    }

    @Override
    @Transactional
    public QuestionResponseStreamEvent appendTokenEvent(ResponseId responseId, String tokenText) {
        Objects.requireNonNull(tokenText, "tokenText must not be null");

        QuestionResponseEntity entity = loadForUpdate(responseId);
        ensureStatus(entity, QuestionResponseStatus.STREAMING);

        int nextSequence = entity.getLastEventSequence() + 1;
        Instant now = clock.instant();
        QuestionResponseEventEntity event = QuestionResponseEventEntity.create(
                entity.getId(),
                nextSequence,
                QuestionResponseStatusMapper.toStorage(QuestionResponseEventType.TOKEN),
                tokenText,
                now);

        eventRepository.save(event);
        entity.setLastEventSequence(nextSequence);
        entity.setAccumulatedText(entity.getAccumulatedText() + tokenText);
        entity.setUpdatedAt(now);

        return toDomainEvent(event);
    }

    @Override
    @Transactional
    public QuestionResponseStreamEvent markCompleted(ResponseId responseId, String finalQuestion) {
        Objects.requireNonNull(finalQuestion, "finalQuestion must not be null");

        QuestionResponseEntity entity = loadForUpdate(responseId);
        QuestionResponseStatus nextStatus = QuestionResponseStatuses.toCompleted(
                QuestionResponseStatusMapper.toDomain(entity.getStatus()));

        int nextSequence = entity.getLastEventSequence() + 1;
        Instant now = clock.instant();
        QuestionResponseEventEntity event = QuestionResponseEventEntity.create(
                entity.getId(),
                nextSequence,
                QuestionResponseStatusMapper.toStorage(QuestionResponseEventType.COMPLETED),
                finalQuestion,
                now);

        eventRepository.save(event);
        entity.setStatus(QuestionResponseStatusMapper.toStorage(nextStatus));
        entity.setFinalText(finalQuestion);
        entity.setLastEventSequence(nextSequence);
        entity.setUpdatedAt(now);

        return toDomainEvent(event);
    }

    @Override
    @Transactional
    public QuestionResponseStreamEvent markFailed(ResponseId responseId, String safeMessage) {
        Objects.requireNonNull(safeMessage, "safeMessage must not be null");

        QuestionResponseEntity entity = loadForUpdate(responseId);
        QuestionResponseStatus nextStatus = QuestionResponseStatuses.toFailed(
                QuestionResponseStatusMapper.toDomain(entity.getStatus()));

        int nextSequence = entity.getLastEventSequence() + 1;
        Instant now = clock.instant();
        QuestionResponseEventEntity event = QuestionResponseEventEntity.create(
                entity.getId(),
                nextSequence,
                QuestionResponseStatusMapper.toStorage(QuestionResponseEventType.ERROR),
                safeMessage,
                now);

        eventRepository.save(event);
        entity.setStatus(QuestionResponseStatusMapper.toStorage(nextStatus));
        entity.setFailureMessage(safeMessage);
        entity.setLastEventSequence(nextSequence);
        entity.setUpdatedAt(now);

        return toDomainEvent(event);
    }

    @Override
    @Transactional
    public void markCancelled(ResponseId responseId) {
        QuestionResponseEntity entity = loadForUpdate(responseId);
        QuestionResponseStatus nextStatus = QuestionResponseStatuses.toCancelled(
                QuestionResponseStatusMapper.toDomain(entity.getStatus()));
        entity.setStatus(QuestionResponseStatusMapper.toStorage(nextStatus));
        entity.setUpdatedAt(clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionResponseStreamEvent> eventsAfter(ResponseId responseId, int afterSequence) {
        Objects.requireNonNull(responseId, "responseId must not be null");
        if (afterSequence < 0) {
            throw new IllegalArgumentException("afterSequence must not be negative");
        }

        return eventRepository.findByResponseIdAndSequenceGreaterThan(responseId.value(), afterSequence)
                .stream()
                .map(this::toDomainEvent)
                .toList();
    }

    private QuestionResponseEntity loadForUpdate(ResponseId responseId) {
        Objects.requireNonNull(responseId, "responseId must not be null");
        return responseRepository.findByIdForUpdate(responseId.value())
                .orElseThrow(() -> new QuestionResponseNotFoundException(responseId));
    }

    private void ensureStatus(QuestionResponseEntity entity, QuestionResponseStatus expectedStatus) {
        QuestionResponseStatus currentStatus = QuestionResponseStatusMapper.toDomain(entity.getStatus());
        if (currentStatus != expectedStatus) {
            throw new InvalidQuestionResponseStatusTransitionException(currentStatus, expectedStatus);
        }
    }

    private QuestionResponse toDomain(QuestionResponseEntity entity) {
        return new QuestionResponse(
                new ResponseId(entity.getId()),
                new SessionId(entity.getSessionId()),
                QuestionResponseStatusMapper.toDomain(entity.getStatus()),
                entity.getAccumulatedText(),
                entity.getFinalText(),
                entity.getLastEventSequence(),
                entity.getFailureMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private QuestionResponseStreamEvent toDomainEvent(QuestionResponseEventEntity entity) {
        return new QuestionResponseStreamEvent(
                new ResponseId(entity.getId().getResponseId()),
                entity.getId().getSequence(),
                QuestionResponseStatusMapper.toEventDomain(entity.getEventType()),
                entity.getPayload(),
                entity.getCreatedAt());
    }

}
