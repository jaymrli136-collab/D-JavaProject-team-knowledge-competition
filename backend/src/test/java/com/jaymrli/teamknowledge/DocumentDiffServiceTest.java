package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeSection;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.service.AccessControlService;
import com.jaymrli.teamknowledge.service.DocumentDiffService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentDiffServiceTest {
    @Test
    void shouldReportAddedAndRemovedLines() {
        KnowledgeStore store = new KnowledgeStore();
        store.addProject(new Project("p1", "项目", "说明", "u1", Set.of("u1"), "active"));
        store.addDocument(document("d1", 1, "保留行\n旧规则"));
        store.addDocument(document("d2", 2, "保留行\n新规则"));
        DocumentDiffService service = new DocumentDiffService(store, new AccessControlService(store));
        var diff = service.diff("u1", "d1", "d2");
        assertThat(diff.added()).contains("新规则").doesNotContain("旧规则");
        assertThat(diff.removed()).contains("旧规则").doesNotContain("新规则");
    }

    private KnowledgeDocument document(String id, int version, String content) {
        return new KnowledgeDocument(id, "p1", "规则", "markdown", "doc://rule", version,
                LocalDate.of(2026, 11, version), Set.of(), content, content,
                List.of(new KnowledgeSection("s" + version, "规则", content, 1)), Instant.now(), "rule");
    }
}
