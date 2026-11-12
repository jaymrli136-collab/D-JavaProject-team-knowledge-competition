package com.jaymrli.teamknowledge.api;

import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGap;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectDecision;
import com.jaymrli.teamknowledge.domain.Domain.ProjectMemory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record AskRequest(
            @NotBlank(message = "question must not be blank") String question,
            List<String> projectIds,
            String sessionId
    ) {
    }

    public record AskResponse(
            String runId,
            String sessionId,
            String status,
            String answer,
            List<String> projectIds,
            double confidence,
            double citationCoverage,
            List<CitationView> citations,
            List<EvidenceView> evidence,
            List<MemoryHintView> memoryHints,
            List<TraceView> trace,
            Instant createdAt
    ) {
    }

    public record CitationView(
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
            double score
    ) {
    }

    public record EvidenceView(
            String evidenceId,
            String projectId,
            String projectName,
            String documentId,
            String documentTitle,
            String sectionId,
            String headingPath,
            String snippet,
            LocalDate effectiveDate,
            double score
    ) {
    }

    public record MemoryHintView(
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

    public record SectionDetailView(
            String projectId,
            String projectName,
            String documentId,
            String documentTitle,
            String sectionId,
            String headingPath,
            String content,
            String sourceUri,
            int version,
            LocalDate effectiveDate,
            String status,
            String supersededBy
    ) {
    }

    public record TraceView(
            String stage,
            String status,
            String detail,
            long durationMs
    ) {
    }

    public record ProjectView(
            String id,
            String name,
            String description,
            String ownerId,
            String status,
            Set<String> memberIds,
            java.util.Map<String, String> memberRoles,
            int documentCount,
            int decisionCount
    ) {
    }

    public record CreateProjectRequest(
            @NotBlank(message = "name must not be blank") String name,
            String description,
            Set<String> memberIds
    ) {
    }

    public record UpdateRoleRequest(
            @NotBlank(message = "memberId must not be blank") String memberId,
            @NotBlank(message = "role must not be blank") String role
    ) {
    }

    public record UpdateMembersRequest(
            @NotNull(message = "memberIds must not be null") Set<String> memberIds
    ) {
    }

    public record DocumentView(
            String id,
            String projectId,
            String title,
            String sourceType,
            String sourceUri,
            int version,
            LocalDate effectiveDate,
            Set<String> tags,
            String l0Summary,
            int sectionCount,
            String lineageKey,
            String status,
            String supersededBy
    ) {
    }

    public record IngestDocumentRequest(
            @NotBlank(message = "title must not be blank") String title,
            @NotBlank(message = "content must not be blank") String content,
            @NotBlank(message = "sourceUri must not be blank") String sourceUri,
            @NotNull(message = "effectiveDate must not be null") LocalDate effectiveDate,
            Integer version,
            Set<String> tags,
            String lineageKey
    ) {
        public IngestDocumentRequest(String title, String content, String sourceUri,
                                     LocalDate effectiveDate, Integer version, Set<String> tags) {
            this(title, content, sourceUri, effectiveDate, version, tags, null);
        }
    }

    public record MemoryProposalRequest(
            @NotBlank(message = "projectId must not be blank") String projectId,
            @NotBlank(message = "type must not be blank") String type,
            @NotBlank(message = "title must not be blank") String title,
            @NotBlank(message = "content must not be blank") String content,
            @NotBlank(message = "sourceRunId must not be blank") String sourceRunId,
            @NotEmpty(message = "evidenceIds must not be empty") List<String> evidenceIds
    ) {
    }

    public record RetrievalPreviewRequest(
            @NotBlank(message = "question must not be blank") String question,
            List<String> projectIds
    ) {
    }

    public record RetrievalPreviewView(
            List<String> projectIds,
            String intent,
            List<String> queries,
            List<EvidenceView> evidence,
            List<MemoryHintView> memoryHints
    ) {
    }

    public record DocumentLifecycleView(
            String documentId,
            String status,
            String supersededBy
    ) {
    }

    public record DocumentDiffView(
            String leftDocumentId,
            int leftVersion,
            String rightDocumentId,
            int rightVersion,
            List<String> added,
            List<String> removed
    ) {
    }

    public record ProjectArchiveView(
            String schemaVersion,
            Instant exportedAt,
            Project project,
            List<KnowledgeDocument> documents,
            List<DocumentLifecycleView> lifecycles,
            List<ProjectDecision> decisions,
            List<ProjectMemory> memories,
            List<KnowledgeGap> gaps
    ) {
    }

    public record UserView(String id, String displayName) {
    }

    public record RuntimeConfigView(
            String provider,
            String model,
            String embeddingModel,
            boolean realEmbeddingEnabled,
            int maxEvidence,
            long maxRunMillis,
            String promptVersion
    ) {
    }

    public record KnowledgeGapView(
            String id,
            String createdBy,
            String question,
            Set<String> projectIds,
            List<String> projectNames,
            List<String> sourceRunIds,
            String status,
            int occurrences,
            Instant createdAt,
            Instant lastSeenAt,
            String candidateDocumentId,
            String candidateDocumentTitle,
            String resolvedBy,
            Instant resolvedAt,
            String resolutionNote
    ) {
    }

    public record ResolveGapRequest(String note) {
    }

    public record MemoryView(
            String id,
            String projectId,
            String projectName,
            String type,
            String title,
            String content,
            String status,
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

    public record EvaluationCaseResult(
            String id,
            String question,
            String userId,
            String expected,
            String actualStatus,
            boolean passed,
            boolean evidenceMatched,
            List<String> failures,
            String runId
    ) {
    }

    public record FeedbackRequest(
            @NotBlank(message = "rating must not be blank") String rating,
            String comment
    ) {
    }

    public record FeedbackView(
            String id,
            String runId,
            String userId,
            String rating,
            String comment,
            Instant createdAt
    ) {
    }

    public record FeedbackSummary(long total, long helpful, long notHelpful, double helpfulRate) {
    }

    public record RunMetricsView(
            long total,
            long answered,
            long abstained,
            double averageDurationMs,
            long p95DurationMs,
            double averageCitationCoverage
    ) {
    }

    public record RunSummaryView(
            String runId,
            String sessionId,
            String question,
            String status,
            List<String> projectIds,
            List<String> citationIds,
            Instant createdAt,
            long durationMs
    ) {
    }

    public record KnowledgeHealthView(
            int activeDocuments,
            int supersededDocuments,
            int archivedDocuments,
            int decisions,
            int activeMemories,
            int proposedMemories,
            int openGaps,
            long feedbackTotal,
            double helpfulRate,
            Double lastEvaluationPassRate,
            Instant lastEvaluationAt,
            List<String> warnings
    ) {
    }

    public record EvaluationCaseView(
            String id,
            String question,
            String userId,
            String expectedStatus,
            List<String> answerKeywords,
            List<String> evidenceKeywords,
            Set<String> requiredProjectIds
    ) {
    }

    public record EvaluationSnapshotView(
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

    public record EvaluationReport(
            int total,
            int passed,
            double passRate,
            double citationCoverage,
            double evidenceRecall,
            double abstentionAccuracy,
            int permissionViolations,
            List<EvaluationCaseResult> cases
    ) {
    }

    public record ApiError(String code, String message, Instant timestamp) {
    }
}
