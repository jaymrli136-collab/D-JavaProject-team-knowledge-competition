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

    public enum MemoryType {
        DECISION, CONSTRAINT, RISK, OWNER, TERM, EXPERIENCE
    }

    public enum MemoryStatus {
        PROPOSED, ACTIVE, RETIRED
    }

    public enum KnowledgeGapStatus {
        OPEN, IN_REVIEW, RESOLVED, IGNORED
    }

    public enum DocumentStatus {
        ACTIVE, SUPERSEDED, ARCHIVED
    }

    public enum ProjectRole {
        OWNER, EDITOR, VIEWER
    }

    public enum FeedbackRating {
        HELPFUL, NOT_HELPFUL
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
            Instant indexedAt,
            String lineageKey
    ) {
        public KnowledgeDocument(String id, String projectId, String title, String sourceType,
                                 String sourceUri, int version, LocalDate effectiveDate, Set<String> tags,
                                 String l0Summary, String l1Overview, List<KnowledgeSection> sections,
                                 Instant indexedAt) {
            this(id, projectId, title, sourceType, sourceUri, version, effectiveDate, tags,
                    l0Summary, l1Overview, sections, indexedAt, null);
        }
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

    public record RunFeedback(
            String id,
            String runId,
            String userId,
            FeedbackRating rating,
            String comment,
            Instant createdAt
    ) {
    }

    public record EvaluationSnapshot(
            String id,
            Instant createdAt,
            int total,
            int passed,
            double passRate,
            double citationCoverage,
            double evidenceRecall,
            double abstentionAccuracy,
            int permissionViolations
    ) {
    }

    public record KnowledgeGap(
            String id,
            String createdBy,
            String question,
            Set<String> projectIds,
            List<String> sourceRunIds,
            KnowledgeGapStatus status,
            int occurrences,
            Instant createdAt,
            Instant lastSeenAt,
            String candidateDocumentId,
            String resolvedBy,
            Instant resolvedAt,
            String resolutionNote
    ) {
    }

    public record MemoryHint(
            String memoryId,
            String projectId,
            String projectName,
            String type,
            String title,
            String content,
            double score,
            List<String> sourceEvidenceIds
    ) {
    }

    public record ProjectMemory(
            String id,
            String projectId,
            MemoryType type,
            String title,
            String content,
            MemoryStatus status,
            double confidence,
            String sourceRunId,
            List<String> sourceEvidenceIds,
            String createdBy,
            Instant createdAt,
            String confirmedBy,
            Instant confirmedAt,
            String retiredBy,
            Instant retiredAt
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

    public record RunMetrics(
            long total,
            long answered,
            long abstained,
            double averageDurationMs,
            long p95DurationMs,
            double averageCitationCoverage
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
            double citationCoverage,
            List<Evidence> evidence,
            List<String> citationIds,
            List<MemoryHint> memoryHints,
            List<TraceStep> trace,
            Instant createdAt,
            long durationMs
    ) {
    }
}
