package com.interviewai.session.adapter.out.persistence;

import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.Message;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.Transcript;
import com.interviewai.shared.SessionId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter mapping the {@link InterviewSession} aggregate to and from
 * its JPA representation.
 */
@Component
class SessionPersistenceAdapter implements SessionRepository {

    private final InterviewSessionJpaRepository repository;

    SessionPersistenceAdapter(InterviewSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(InterviewSession session) {
        InterviewSessionEntity entity = repository.findById(session.id().value())
                .orElseGet(() -> InterviewSessionEntity.create(
                        session.id().value(), SessionStateMapper.toStorage(session.state())));
        entity.setState(SessionStateMapper.toStorage(session.state()));
        appendNewMessages(entity, session.transcript().messages());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewSession> findById(SessionId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    private void appendNewMessages(InterviewSessionEntity entity, List<Message> messages) {
        for (int index = entity.messageCount(); index < messages.size(); index++) {
            Message message = messages.get(index);
            entity.addMessage(message.role().name(), message.content(), message.timestamp());
        }
    }

    private InterviewSession toDomain(InterviewSessionEntity entity) {
        Transcript transcript = Transcript.empty();
        for (MessageEntity message : entity.getMessages()) {
            transcript = transcript.append(toDomainMessage(message));
        }
        return new InterviewSession(
                new SessionId(entity.getId()),
                SessionStateMapper.fromStorage(entity.getState()),
                transcript);
    }

    private Message toDomainMessage(MessageEntity entity) {
        return new Message(MessageRole.valueOf(entity.getRole()), entity.getContent(), entity.getCreatedAt());
    }
}
