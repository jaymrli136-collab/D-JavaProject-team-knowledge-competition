package com.jaymrli.teamknowledge.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionMemoryService {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState get(String sessionId) { return sessions.get(sessionId); }

    public void update(String sessionId, String question, Set<String> projectIds, String runId) {
        sessions.put(sessionId, new SessionState(question, Set.copyOf(projectIds), runId, Instant.now()));
    }

    public record SessionState(String lastQuestion, Set<String> projectIds, String lastRunId, Instant updatedAt) {}
}
