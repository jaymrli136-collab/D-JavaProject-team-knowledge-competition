package com.jaymrli.teamknowledge.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class Domain {
    private Domain() {
    }

    public enum Intent {
        FACT, DECISION, RISK, STATUS, COMPARISON, UNKNOWN
    }

    public enum RunStatus {
        ANSWERED, ABSTAINED, FORBIDDEN
    }

    public record Project(
            String id,
            String name,
            String description,
            String ownerId,
            Set<String> memberIds,
            String status
    ) {
        public boolean canAccess(String userId) {
            return memberIds != null && memberIds.contains(userId);
        }
    }

    public record KnowledgeSection(
            String id,
            String headingPath,
            String content,
            int order
    ) {
    }

    public record KnowledgeDocument(
            String id,
            String projectId,
            String title,
            String sourceType,
            String sourceUri,
            int version,
            LocalDate effectiveDate,
            Set<String> tags,
            String l0Summary,
            String l1Overview,
            List<KnowledgeSection> sections,
            Instant indexedAt
    ) {
    }

    public record ProjectDecision(
            String id,
            String projectId,
            String title,
            String decision,
            String rationale,
            String ownerId,
            String sourceDocumentId,
            LocalDate effectiveDate
    ) {
    }

    public record Plan(
            Intent intent,
            List<String> queries,
            Set<String> projectIds,
            String reason
    ) {
    }

    public record Evidence(
            String evidenceId,
            String projectId,
            String projectName,
            String documentId,
            String documentTitle,
            String sectionId,
            String headingPath,
            String snippet,
            String sourceUri,
            int version,
            LocalDate effectiveDate,
            double bm25,
            double vector,
            double finalScore
    ) {
    }

    public record AgentAnswer(
            String answer,
            List<String> citationIds,
            boolean abstained,
            double confidence
    ) {
    }

    public record ToolExecution(
            String toolName,
            String reason,
            List<Evidence> evidence
    ) {
    }

    public record TraceStep(
            String stage,
            String status,
            String detail,
            long durationMs
    ) {
    }

    public record AgentRun(
            String runId,
            String sessionId,
            String userId,
            String question,
            Plan plan,
            RunStatus status,
            String answer,
            List<Evidence> evidence,
            List<String> citationIds,
            List<TraceStep> trace,
            Instant createdAt,
            long durationMs
    ) {
    }
}
