package com.jaymrli.teamknowledge.api;

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
            List<CitationView> citations,
            List<EvidenceView> evidence,
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
            String documentTitle,
            String headingPath,
            String snippet,
            LocalDate effectiveDate,
            double score
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
            int documentCount,
            int decisionCount
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
            int sectionCount
    ) {
    }

    public record IngestDocumentRequest(
            @NotBlank(message = "title must not be blank") String title,
            @NotBlank(message = "content must not be blank") String content,
            @NotBlank(message = "sourceUri must not be blank") String sourceUri,
            @NotNull(message = "effectiveDate must not be null") LocalDate effectiveDate,
            Integer version,
            Set<String> tags
    ) {
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
            List<String> failures,
            String runId
    ) {
    }

    public record EvaluationReport(
            int total,
            int passed,
            double passRate,
            double citationCoverage,
            double abstentionAccuracy,
            int permissionViolations,
            List<EvaluationCaseResult> cases
    ) {
    }

    public record ApiError(String code, String message, Instant timestamp) {
    }
}
