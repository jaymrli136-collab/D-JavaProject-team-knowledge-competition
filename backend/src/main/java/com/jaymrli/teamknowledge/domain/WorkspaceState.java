package com.jaymrli.teamknowledge.domain;

import com.jaymrli.teamknowledge.domain.Domain.EvaluationSnapshot;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGap;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectDecision;
import com.jaymrli.teamknowledge.domain.Domain.ProjectMemory;
import com.jaymrli.teamknowledge.domain.Domain.ProjectRole;
import com.jaymrli.teamknowledge.domain.Domain.RunFeedback;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkspaceState(
        int version,
        Instant savedAt,
        List<Project> projects,
        List<KnowledgeDocument> documents,
        List<ProjectDecision> decisions,
        Map<String, String> documentStatuses,
        Map<String, String> supersededBy,
        List<ProjectMemory> memories,
        Map<String, ProjectRole> projectRoles,
        List<KnowledgeGap> gaps,
        List<RunFeedback> feedback,
        List<EvaluationSnapshot> evaluations
) {
}
