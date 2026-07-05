package com.interviewai.session.application.port.out;

import com.interviewai.session.domain.InterviewSession;
import com.interviewai.shared.SessionId;

import java.util.Optional;

/**
 * Persistence port for the interview session aggregate.
 * <p>
 * Implemented by an adapter in the infrastructure layer; the domain and application
 * layers depend only on this abstraction.
 */
public interface SessionRepository {

    /**
     * Persists the current state of the given session, inserting it if new or
     * updating it otherwise.
     */
    void save(InterviewSession session);

    /**
     * Loads the session identified by the given id, if present.
     */
    Optional<InterviewSession> findById(SessionId id);
}
