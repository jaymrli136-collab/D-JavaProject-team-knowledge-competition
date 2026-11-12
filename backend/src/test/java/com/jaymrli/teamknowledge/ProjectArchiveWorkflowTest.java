package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.service.ProjectArchiveService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectArchiveWorkflowTest {
    @Autowired
    private ProjectArchiveService archiveService;

    @Autowired
    private KnowledgeStore knowledgeStore;

    @Test
    void shouldExportAndImportProjectIntoNewOwnership() {
        var archivedDocument = knowledgeStore.documentsForProjects(java.util.Set.of("p_tomato")).stream()
                .filter(item -> item.title().contains("指标口径")).findFirst().orElseThrow();
        knowledgeStore.archiveDocument(archivedDocument.id());

        var exported = archiveService.exportProject("u_alice", "p_tomato");
        assertThat(exported.documents()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(exported.decisions()).isNotEmpty();

        var imported = archiveService.importProject("u_carol", exported);
        assertThat(imported.project().id()).isNotEqualTo("p_tomato");
        assertThat(imported.project().ownerId()).isEqualTo("u_carol");
        assertThat(imported.documents()).hasSameSizeAs(exported.documents());
        assertThat(imported.decisions()).isNotEmpty();
        assertThat(imported.decisions()).allMatch(item -> item.projectId().equals(imported.project().id()));
        assertThat(imported.documents()).allMatch(item -> item.projectId().equals(imported.project().id()));
        assertThat(imported.lifecycles()).anyMatch(item -> "ARCHIVED".equals(item.status()));
    }
}
