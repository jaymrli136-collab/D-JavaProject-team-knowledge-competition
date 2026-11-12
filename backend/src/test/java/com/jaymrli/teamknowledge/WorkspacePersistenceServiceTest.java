package com.jaymrli.teamknowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeSection;
import com.jaymrli.teamknowledge.domain.Domain.MemoryStatus;
import com.jaymrli.teamknowledge.domain.Domain.MemoryType;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.ProjectMemory;
import com.jaymrli.teamknowledge.repository.EvaluationHistoryRepository;
import com.jaymrli.teamknowledge.repository.FeedbackRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeGapRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.repository.ProjectMemoryRepository;
import com.jaymrli.teamknowledge.repository.ProjectRoleRepository;
import com.jaymrli.teamknowledge.service.WorkspacePersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspacePersistenceServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndRestoreWorkspace() {
        KnowledgeStore store = new KnowledgeStore();
        ProjectMemoryRepository memories = new ProjectMemoryRepository();
        KnowledgeGapRepository gaps = new KnowledgeGapRepository();
        FeedbackRepository feedback = new FeedbackRepository();
        EvaluationHistoryRepository evaluations = new EvaluationHistoryRepository();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        store.addProject(new Project("p1", "项目一", "说明", "u1", Set.of("u1"), "active"));
        store.addDocument(new KnowledgeDocument("d1", "p1", "文档", "markdown", "doc://1",
                1, LocalDate.of(2026, 11, 1), Set.of("tag"), "摘要", "概览",
                List.of(new KnowledgeSection("s1", "结论", "内容", 1)), Instant.now(), "lineage"));
        memories.save(new ProjectMemory("m1", "p1", MemoryType.RISK, "风险", "内容", MemoryStatus.ACTIVE,
                0.8, "run", List.of("E1"), "u1", Instant.now(), "u1", Instant.now(), null, null));

        Path stateFile = tempDir.resolve("workspace-state.json");
        WorkspacePersistenceService writer = new WorkspacePersistenceService(store, memories, gaps, feedback,
                evaluations, new ProjectRoleRepository(), mapper, stateFile.toString(), true);
        writer.save();
        assertThat(stateFile).exists();

        KnowledgeStore restoredStore = new KnowledgeStore();
        ProjectMemoryRepository restoredMemories = new ProjectMemoryRepository();
        WorkspacePersistenceService reader = new WorkspacePersistenceService(restoredStore, restoredMemories,
                new KnowledgeGapRepository(), new FeedbackRepository(), new EvaluationHistoryRepository(),
                new ProjectRoleRepository(), mapper, stateFile.toString(), true);
        reader.restoreOnStartup();

        assertThat(restoredStore.allProjects()).extracting(Project::id).containsExactly("p1");
        assertThat(restoredStore.allDocuments()).extracting(KnowledgeDocument::id).containsExactly("d1");
        assertThat(restoredMemories.all()).extracting(ProjectMemory::id).containsExactly("m1");
    }
}
