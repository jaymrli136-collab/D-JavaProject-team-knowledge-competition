package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.CitationView;
import com.jaymrli.teamknowledge.api.ApiDtos.EvidenceView;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryHintView;
import com.jaymrli.teamknowledge.api.ApiDtos.RunMetricsView;
import com.jaymrli.teamknowledge.api.ApiDtos.RunSummaryView;
import com.jaymrli.teamknowledge.api.ApiDtos.TraceView;
import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.AgentRun;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.MemoryHint;
import com.jaymrli.teamknowledge.domain.Domain.Plan;
import com.jaymrli.teamknowledge.domain.Domain.RunStatus;
import com.jaymrli.teamknowledge.domain.Domain.ToolExecution;
import com.jaymrli.teamknowledge.domain.Domain.RunMetrics;
import com.jaymrli.teamknowledge.domain.Domain.TraceStep;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class KnowledgeAgentHarness {
    private final AccessControlService accessControl;
    private final QueryPlanner queryPlanner;
    private final AgentToolRouter toolRouter;
    private final AnswerGenerator answerGenerator;
    private final CitationVerifier citationVerifier;
    private final EvidenceConsistencyChecker consistencyChecker;
    private final ProjectMemoryContextService memoryContext;
    private final KnowledgeGapService knowledgeGapService;
    private final SessionMemoryService sessionMemory;
    private final TeamKnowledgeProperties properties;
    private final Map<String, AgentRun> runs = new ConcurrentHashMap<>();

    public KnowledgeAgentHarness(AccessControlService accessControl, QueryPlanner queryPlanner,
                                 AgentToolRouter toolRouter, AnswerGenerator answerGenerator,
                                 CitationVerifier citationVerifier, EvidenceConsistencyChecker consistencyChecker,
                                 ProjectMemoryContextService memoryContext, KnowledgeGapService knowledgeGapService,
                                 SessionMemoryService sessionMemory,
                                 TeamKnowledgeProperties properties) {
        this.accessControl = accessControl;
        this.queryPlanner = queryPlanner;
        this.toolRouter = toolRouter;
        this.answerGenerator = answerGenerator;
        this.citationVerifier = citationVerifier;
        this.consistencyChecker = consistencyChecker;
        this.memoryContext = memoryContext;
        this.knowledgeGapService = knowledgeGapService;
        this.sessionMemory = sessionMemory;
        this.properties = properties;
    }

    public AskResponse ask(String userId, AskRequest request) {
        return ask(userId, request, ignored -> { });
    }

    public AskResponse ask(String userId, AskRequest request, Consumer<TraceStep> observer) {
        return ask(userId, request, observer, () -> false);
    }

    public AskResponse ask(String userId, AskRequest request, Consumer<TraceStep> observer,
                           BooleanSupplier cancelled) {
        ensureActive(cancelled);
        long startedNanos = System.nanoTime();
        Instant createdAt = Instant.now();
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "session_" + UUID.randomUUID() : request.sessionId();
        SessionMemoryService.SessionState session = sessionMemory.get(sessionId);
        Set<String> previousProjects = session == null ? Set.of() : session.projectIds();

        Set<String> scope = accessControl.resolveScope(userId, request.projectIds(), request.question(), previousProjects);
        String contextualQuestion = contextualQuestion(request.question(), session);
        if (scope.isEmpty()) throw new ForbiddenException("没有可访问的项目");

        ensureActive(cancelled);
        ensureBudget(startedNanos);
        Plan plan = queryPlanner.plan(contextualQuestion, scope);
        List<TraceStep> trace = new ArrayList<>();
        long scopeDone = System.nanoTime();
        record(trace, observer, step("scope", "ok", "用户=" + userId + "，项目=" + String.join(",", scope), startedNanos, scopeDone));
        if (!contextualQuestion.equals(request.question())) {
            record(trace, observer, new TraceStep("rewrite", "ok", "上下文查询=" + contextualQuestion, 0));
        }

        ensureActive(cancelled);
        ensureBudget(startedNanos);
        long memoryStart = System.nanoTime();
        List<MemoryHint> memoryHints = memoryContext.retrieve(contextualQuestion, scope, 3);
        long memoryDone = System.nanoTime();
        record(trace, observer, step("memory", memoryHints.isEmpty() ? "empty" : "ok",
                "已确认记忆命中=" + memoryHints.size(), memoryStart, memoryDone));

        ensureActive(cancelled);
        ensureBudget(startedNanos);
        long retrieveStart = System.nanoTime();
        ToolExecution toolExecution = toolRouter.search(contextualQuestion, scope, plan.intent(),
                properties.retrieval().maxEvidence());
        List<Evidence> evidence = toolExecution.evidence();
        long retrieveDone = System.nanoTime();
        record(trace, observer, step("tool", "ok", "路由=" + toolRouter.route(plan) + "；" + toolExecution.reason(),
                retrieveStart, retrieveDone));
        record(trace, observer, new TraceStep("retrieve", evidence.isEmpty() ? "empty" : "ok",
                "工具=" + toolExecution.toolName() + "，召回证据=" + evidence.size(), Math.max(0, (retrieveDone - retrieveStart) / 1_000_000)));

        ensureActive(cancelled);
        ensureBudget(startedNanos);
        long answerStart = System.nanoTime();
        AgentAnswer generated = answerGenerator.generate(contextualQuestion, evidence, plan.intent(), memoryHints);
        AgentAnswer verified = citationVerifier.verify(generated, evidence);
        verified = new AgentAnswer(consistencyChecker.annotate(evidence, verified.answer()),
                verified.citationIds(), verified.abstained(), verified.confidence());
        long answerDone = System.nanoTime();
        double citationCoverage = citationVerifier.coverage(verified.answer());
        record(trace, observer, step("answer", verified.abstained() ? "abstained" : "ok",
                "引用=" + verified.citationIds() + "，陈述覆盖率=" + citationCoverage, answerStart, answerDone));

        ensureActive(cancelled);
        RunStatus status = verified.abstained() ? RunStatus.ABSTAINED : RunStatus.ANSWERED;
        String runId = "run_" + UUID.randomUUID();
        long finishedNanos = System.nanoTime();
        long durationMs = Math.max(0, (finishedNanos - startedNanos) / 1_000_000);
        record(trace, observer, new TraceStep("verify", "ok",
                "引用校验完成，状态=" + status, Math.max(0, durationMs - 1)));
        if (status == RunStatus.ABSTAINED) {
            var gap = knowledgeGapService.capture(userId, request.question(), scope, runId);
            record(trace, observer, new TraceStep("gap", "open", "知识缺口=" + gap.id() + "，累计次数=" + gap.occurrences(), 0));
        } else {
            record(trace, observer, new TraceStep("gap", "skipped", "回答有证据，无需记录知识缺口", 0));
        }

        AgentRun run = new AgentRun(runId, sessionId, userId, request.question(), plan, status,
                verified.answer(), citationCoverage, List.copyOf(evidence), List.copyOf(verified.citationIds()),
                List.copyOf(memoryHints), List.copyOf(trace), createdAt, durationMs);
        runs.put(runId, run);
        sessionMemory.update(sessionId, contextualQuestion, scope, runId);
        return toResponse(run, verified.confidence(), citationCoverage);
    }

    public RunMetrics metrics(String userId) {
        List<AgentRun> userRuns = runs.values().stream().filter(run -> run.userId().equals(userId)).toList();
        if (userRuns.isEmpty()) return new RunMetrics(0, 0, 0, 0, 0, 0);
        long answered = userRuns.stream().filter(run -> run.status() == RunStatus.ANSWERED).count();
        long abstained = userRuns.stream().filter(run -> run.status() == RunStatus.ABSTAINED).count();
        long[] durations = userRuns.stream().mapToLong(AgentRun::durationMs).sorted().toArray();
        int p95Index = Math.max(0, (int) Math.ceil(durations.length * 0.95) - 1);
        double averageDuration = userRuns.stream().mapToLong(AgentRun::durationMs).average().orElse(0);
        double averageCoverage = userRuns.stream().mapToDouble(AgentRun::citationCoverage).average().orElse(0);
        return new RunMetrics(userRuns.size(), answered, abstained,
                Math.round(averageDuration * 100.0) / 100.0, durations[p95Index],
                Math.round(averageCoverage * 10000.0) / 10000.0);
    }

    public RunMetricsView metricsView(String userId) {
        RunMetrics metrics = metrics(userId);
        return new RunMetricsView(metrics.total(), metrics.answered(), metrics.abstained(),
                metrics.averageDurationMs(), metrics.p95DurationMs(), metrics.averageCitationCoverage());
    }

    public List<RunSummaryView> recentRuns(String userId, int limit) {
        return runs.values().stream()
                .filter(run -> run.userId().equals(userId))
                .sorted(Comparator.comparing(AgentRun::createdAt).reversed())
                .limit(Math.max(0, limit))
                .map(run -> new RunSummaryView(run.runId(), run.sessionId(), run.question(), run.status().name(),
                        run.plan().projectIds().stream().toList(), run.citationIds(), run.createdAt(), run.durationMs()))
                .toList();
    }

    public AgentRun getRun(String userId, String runId) {
        AgentRun run = runs.get(runId);
        if (run == null) throw new NotFoundException("运行记录不存在");
        if (!run.userId().equals(userId)) throw new ForbiddenException("无权查看该运行记录");
        return run;
    }

    private AskResponse toResponse(AgentRun run, double confidence, double citationCoverage) {
        List<EvidenceView> evidenceViews = run.evidence().stream()
                .map(item -> new EvidenceView(item.evidenceId(), item.projectId(), item.projectName(),
                        item.documentId(), item.documentTitle(), item.sectionId(), item.headingPath(),
                        item.snippet(), item.effectiveDate(), item.finalScore()))
                .toList();
        List<CitationView> citationViews = run.evidence().stream()
                .filter(item -> run.citationIds().contains(item.evidenceId()))
                .sorted(Comparator.comparing(Evidence::evidenceId))
                .map(item -> new CitationView(item.evidenceId(), item.projectId(), item.projectName(),
                        item.documentId(), item.documentTitle(), item.sectionId(), item.headingPath(),
                        item.snippet(), item.sourceUri(), item.version(), item.effectiveDate(), item.finalScore()))
                .toList();
        List<TraceView> traceViews = run.trace().stream()
                .map(item -> new TraceView(item.stage(), item.status(), item.detail(), item.durationMs()))
                .toList();
        List<MemoryHintView> hintViews = run.memoryHints().stream()
                .map(item -> new MemoryHintView(item.memoryId(), item.projectId(), item.projectName(),
                        item.type(), item.title(), item.content(), item.score(), item.sourceEvidenceIds()))
                .toList();
        return new AskResponse(run.runId(), run.sessionId(), run.status().name(), run.answer(),
                run.plan().projectIds().stream().toList(), confidence, citationCoverage, citationViews, evidenceViews,
                hintViews, traceViews, run.createdAt());
    }

    private String contextualQuestion(String question, SessionMemoryService.SessionState session) {
        if (session == null || session.lastQuestion() == null || session.lastQuestion().isBlank()) return question;
        List<String> markers = List.of("它", "这个项目", "该项目", "上面", "刚才", "那", "继续", "还有");
        String normalized = question.toLowerCase(java.util.Locale.ROOT);
        if (question.length() <= 48 && markers.stream().anyMatch(normalized::contains)) {
            return session.lastQuestion() + " " + question;
        }
        return question;
    }

    private void ensureBudget(long startedNanos) {
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
        if (elapsedMillis > properties.retrieval().maxRunMillis()) {
            throw new RunCancelledException("Agent run budget exceeded: " + elapsedMillis + "ms");
        }
    }

    private void ensureActive(BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new RunCancelledException("Agent run cancelled");
        }
    }

    private void record(List<TraceStep> trace, Consumer<TraceStep> observer, TraceStep step) {
        trace.add(step);
        try {
            observer.accept(step);
        } catch (RuntimeException ignored) {
            // Observability must never break the Agent run.
        }
    }

    private TraceStep step(String stage, String status, String detail, long startNanos, long endNanos) {
        return new TraceStep(stage, status, detail, Math.max(0, (endNanos - startNanos) / 1_000_000));
    }
}
