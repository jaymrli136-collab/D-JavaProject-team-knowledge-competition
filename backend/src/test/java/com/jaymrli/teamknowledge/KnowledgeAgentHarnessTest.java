package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeSection;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.domain.Domain.TraceStep;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.service.AccessControlService;
import com.jaymrli.teamknowledge.service.AgentToolRouter;
import com.jaymrli.teamknowledge.service.CitationVerifier;
import com.jaymrli.teamknowledge.service.EmbeddingService;
import com.jaymrli.teamknowledge.service.EvidenceConsistencyChecker;
import com.jaymrli.teamknowledge.service.ForbiddenException;
import com.jaymrli.teamknowledge.service.HybridRetriever;
import com.jaymrli.teamknowledge.service.KnowledgeSearchTool;
import com.jaymrli.teamknowledge.repository.KnowledgeGapRepository;
import com.jaymrli.teamknowledge.repository.ProjectMemoryRepository;
import com.jaymrli.teamknowledge.service.KnowledgeGapService;
import com.jaymrli.teamknowledge.service.ProjectMemoryContextService;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import com.jaymrli.teamknowledge.service.QueryPlanner;
import com.jaymrli.teamknowledge.service.RuleBasedAnswerGenerator;
import com.jaymrli.teamknowledge.service.RunCancelledException;
import com.jaymrli.teamknowledge.service.SessionMemoryService;
import com.jaymrli.teamknowledge.service.TextAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeAgentHarnessTest {
    private KnowledgeAgentHarness harness;

    @BeforeEach
    void setUp() {
        KnowledgeStore store = new KnowledgeStore();
        TextAnalyzer analyzer = new TextAnalyzer();
        AccessControlService accessControl = new AccessControlService(store);
        HybridRetriever retriever = new HybridRetriever(store, analyzer,
                new EmbeddingService(analyzer, new TeamKnowledgeProperties(
                        new TeamKnowledgeProperties.Llm("mock", "", "", "", 0.1, "text-embedding-3-small"),
                        new TeamKnowledgeProperties.Retrieval(6, 15000)), org.springframework.web.client.RestClient.builder()));
        harness = new KnowledgeAgentHarness(
                accessControl,
                new QueryPlanner(),
                new AgentToolRouter(new KnowledgeSearchTool(retriever)),
                new RuleBasedAnswerGenerator(),
                new CitationVerifier(),
                new EvidenceConsistencyChecker(),
                new ProjectMemoryContextService(new ProjectMemoryRepository(), store, analyzer),
                new KnowledgeGapService(new KnowledgeGapRepository(), store, accessControl, analyzer),
                new SessionMemoryService(),
                new TeamKnowledgeProperties(
                        new TeamKnowledgeProperties.Llm("mock", "", "", "", 0.1, "text-embedding-3-small"),
                        new TeamKnowledgeProperties.Retrieval(6, 15000))
        );

        store.addProject(new Project("p1", "番茄小说增长实验", "增长项目", "u_alice", Set.of("u_alice"), "active"));
        store.addProject(new Project("p2", "头条热点专题", "热点项目", "u_bob", Set.of("u_bob"), "active"));
        store.addDocument(new KnowledgeDocument(
                "d1", "p1", "十一月风险清单", "markdown", "https://docs.example.com/risk",
                1, LocalDate.of(2026, 11, 10), Set.of("风险"),
                "短剧内容成本上涨。", "风险与应对",
                List.of(new KnowledgeSection("s1", "当前最大风险",
                        "短剧内容采购成本连续上涨，是当前最大风险。需要控制低效采买。", 1)),
                Instant.now()
        ));
    }

    @Test
    void shouldAnswerWithVerifiedCitation() {
        AskResponse response = harness.ask("u_alice",
                new AskRequest("番茄小说当前最大的风险是什么？", List.of("p1"), "s1"));
        assertThat(response.status()).isEqualTo("ANSWERED");
        assertThat(response.answer()).contains("内容");
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.citations()).allMatch(item -> item.projectId().equals("p1"));
        assertThat(response.citationCoverage()).isGreaterThan(0.0);
        assertThat(harness.recentRuns("u_alice", 5)).extracting(item -> item.runId()).contains(response.runId());
        var metrics = harness.metricsView("u_alice");
        assertThat(metrics.total()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.averageCitationCoverage()).isGreaterThan(0.0);
    }

    @Test
    void shouldEmitHarnessTraceEvents() {
        List<TraceStep> events = new ArrayList<>();
        harness.ask("u_alice",
                new AskRequest("番茄小说当前最大的风险是什么？", List.of("p1"), "events"), events::add);
        assertThat(events).extracting(TraceStep::stage)
                .contains("scope", "memory", "tool", "retrieve", "answer", "verify", "gap");
    }

    @Test
    void shouldRewriteShortFollowUpWithSessionContext() {
        harness.ask("u_alice", new AskRequest("番茄小说当前最大的风险是什么？", List.of("p1"), "follow-up"));
        AskResponse response = harness.ask("u_alice",
                new AskRequest("那它具体怎么处理？", List.of("p1"), "follow-up"));
        assertThat(response.status()).isEqualTo("ANSWERED");
        assertThat(response.trace()).extracting(item -> item.stage()).contains("rewrite");
    }

    @Test
    void shouldStopRunWhenCancellationTokenIsActive() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        assertThatThrownBy(() -> harness.ask("u_alice",
                new AskRequest("番茄小说风险", List.of("p1"), "cancelled"), ignored -> { }, cancelled::get))
                .isInstanceOf(RunCancelledException.class);
    }

    @Test
    void shouldRejectUnauthorizedProjectMention() {
        assertThatThrownBy(() -> harness.ask("u_alice",
                new AskRequest("头条热点专题有什么风险？", List.of(), "s2")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void shouldAbstainWhenNoAnchorEvidenceExists() {
        AskResponse response = harness.ask("u_alice",
                new AskRequest("quarterly budget forecast", List.of("p1"), "s3"));
        assertThat(response.status()).isEqualTo("ABSTAINED");
        assertThat(response.citations()).isEmpty();
    }
}
