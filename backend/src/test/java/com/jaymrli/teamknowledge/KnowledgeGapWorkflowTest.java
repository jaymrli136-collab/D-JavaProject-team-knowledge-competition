package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.IngestDocumentRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.KnowledgeGapView;
import com.jaymrli.teamknowledge.service.DocumentIngestionService;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import com.jaymrli.teamknowledge.service.KnowledgeGapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KnowledgeGapWorkflowTest {
    @Autowired
    private KnowledgeAgentHarness harness;

    @Autowired
    private KnowledgeGapService gapService;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Test
    void shouldCaptureAccumulateAndResolveKnowledgeGap() {
        String question = "番茄小说在南极洲的用户覆盖率是多少？";
        AskResponse first = harness.ask("u_alice", new AskRequest(question, List.of("p_tomato"), "gap-1"));
        assertThat(first.status()).isEqualTo("ABSTAINED");

        AskResponse second = harness.ask("u_alice", new AskRequest(question, List.of("p_tomato"), "gap-2"));
        assertThat(second.status()).isEqualTo("ABSTAINED");

        KnowledgeGapView gap = gapService.listVisible("u_alice").stream()
                .filter(item -> item.question().equals(question))
                .findFirst().orElseThrow();
        assertThat(gap.status()).isEqualTo("OPEN");
        assertThat(gap.occurrences()).isEqualTo(2);
        assertThat(gapService.listVisible("u_alice", "p_duanju"))
                .noneMatch(item -> item.question().equals(question));
        assertThat(gap.sourceRunIds()).contains(first.runId(), second.runId());
        assertThat(gapService.exportMarkdown("u_alice", "p_tomato"))
                .contains("# 知识缺口清单").contains(question).contains("OPEN");

        var document = ingestionService.ingest("u_alice", "p_tomato", new IngestDocumentRequest(
                "南极洲用户覆盖调研", "# 调研\n南极洲市场用户覆盖率完成初步调研，南极洲用户覆盖率仍需复核。",
                "https://docs.example.com/tomato/antarctica", LocalDate.of(2026, 11, 12), 1,
                Set.of("调研"), "antarctica-coverage"));
        KnowledgeGapView inReview = gapService.listVisible("u_alice").stream()
                .filter(item -> item.id().equals(gap.id())).findFirst().orElseThrow();
        assertThat(inReview.status()).isEqualTo("IN_REVIEW");
        assertThat(inReview.candidateDocumentId()).isEqualTo(document.id());
        assertThat(inReview.candidateDocumentTitle()).isEqualTo("南极洲用户覆盖调研");

        KnowledgeGapView resolved = gapService.resolve("u_bob", gap.id(), "已补充目标地区市场调研文档");
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.resolvedBy()).isEqualTo("u_bob");
        assertThat(resolved.resolutionNote()).contains("市场调研");
    }
}
