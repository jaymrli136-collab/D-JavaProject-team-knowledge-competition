package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.domain.Domain.DocumentStatus;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeSection;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentLifecycleTest {
    @Test
    void newerVersionShouldSupersedeOlderVersion() {
        KnowledgeStore store = new KnowledgeStore();
        store.addProject(new Project("p1", "测试项目", "测试", "u1", Set.of("u1"), "active"));
        KnowledgeDocument v1 = document("d1", 1, "旧版上线时间为12月15日。");
        KnowledgeDocument v2 = document("d2", 2, "新版上线时间为12月8日。");
        store.addDocument(v1);
        store.addDocument(v2);

        assertThat(store.status(v1.id())).isEqualTo(DocumentStatus.SUPERSEDED);
        assertThat(store.supersededBy(v1.id())).isEqualTo(v2.id());
        assertThat(store.status(v2.id())).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(store.activeDocumentsForProjects(Set.of("p1")))
                .extracting(KnowledgeDocument::id).containsExactly("d2");
        assertThat(store.historyFor("d1")).extracting(KnowledgeDocument::id).containsExactly("d2", "d1");
        store.archiveDocument("d2");
        assertThat(store.status("d2")).isEqualTo(DocumentStatus.ARCHIVED);
        assertThat(store.activeDocumentsForProjects(Set.of("p1"))).isEmpty();
        store.restoreDocument("d2");
        assertThat(store.status("d2")).isEqualTo(DocumentStatus.ACTIVE);
        assertThat(store.activeDocumentsForProjects(Set.of("p1")))
                .extracting(KnowledgeDocument::id).containsExactly("d2");
    }

    private KnowledgeDocument document(String id, int version, String content) {
        return new KnowledgeDocument(id, "p1", "上线计划", "markdown", "https://docs.example.com/launch",
                version, LocalDate.of(2026, 11, version), Set.of("上线"), content, "上线计划",
                List.of(new KnowledgeSection("s" + version, "决策", content, 1)), Instant.now(),
                "tomato-launch");
    }
}
