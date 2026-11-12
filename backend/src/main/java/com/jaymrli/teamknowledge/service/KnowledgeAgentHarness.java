package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.CitationView;
import com.jaymrli.teamknowledge.api.ApiDtos.EvidenceView;
import com.jaymrli.teamknowledge.api.ApiDtos.TraceView;
import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.AgentRun;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.Plan;
import com.jaymrli.teamknowledge.domain.Domain.RunStatus;
import com.jaymrli.teamknowledge.domain.Domain.ToolExecution;
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

@Service
public class KnowledgeAgentHarness {
    private final AccessControlService accessControl;
    private final QueryPlanner queryPlanner;
    private final AgentToolRouter toolRouter;
    private final AnswerGenerator answerGenerator;
    private final CitationVerifier citationVerifier;
    private final EvidenceConsistencyChecker consistencyChecker;
    private final SessionMemoryService sessionMemory;
    private final TeamKnowledgeProperties properties;
    private final Map<String, AgentRun> runs = new ConcurrentHashMap<>();

    public KnowledgeAgentHarness(AccessControlService accessControl, QueryPlanner queryPlanner,
                                 AgentToolRouter toolRouter, AnswerGenerator answerGenerator,
                                 CitationVerifier citationVerifier, EvidenceConsistencyChecker consistencyChecker,
                                 SessionMemoryService sessionMemory,
                                 TeamKnowledgeProperties properties) {
        this.accessControl = accessControl;
        this.queryPlanner = queryPlanner;
        this.toolRouter = toolRouter;
        this.answerGenerator = answerGenerator;
        this.citationVerifier = citationVerifier;
        this.consistencyChecker = consistencyChecker;
        this.sessionMemory = sessionMemory;
        this.properties = properties;
    }

    public AskResponse ask(String userId, AskRequest request) {
        long startedNanos = System.nanoTime();
        Instant createdAt = Instant.now();
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "session_" + UUID.randomUUID() : request.sessionId();
        SessionMemoryService.SessionState session = sessionMemory.get(sessionId);
        Set<String> previousProjects = session == null ? Set.of() : session.projectIds();

        Set<String> scope = accessControl.resolveScope(userId, request.projectIds(), request.question(), previousProjects);
        if (scope.isEmpty()) throw new ForbiddenException("没有可访问的项目");

        Plan plan = queryPlanner.plan(request.question(), scope);
        List<TraceStep> trace = new ArrayList<>();
        long scopeDone = System.nanoTime();
        trace.add(step("scope", "ok", "用户=" + userId + "，项目=" + String.join(",", scope), startedNanos, scopeDone));

        long retrieveStart = System.nanoTime();
        ToolExecution toolExecution = toolRouter.search(request.question(), scope, plan.intent(),
                properties.retrieval().maxEvidence());
        List<Evidence> evidence = toolExecution.evidence();
        long retrieveDone = System.nanoTime();
        trace.add(step("tool", "ok", "路由=" + toolRouter.route(plan) + "；" + toolExecution.reason(),
                retrieveStart, retrieveDone));
        trace.add(new TraceStep("retrieve", evidence.isEmpty() ? "empty" : "ok",
                "工具=" + toolExecution.toolName() + "，召回证据=" + evidence.size(), Math.max(0, (retrieveDone - retrieveStart) / 1_000_000)));

        long answerStart = System.nanoTime();
        AgentAnswer generated = answerGenerator.generate(request.question(), evidence, plan.intent());
        AgentAnswer verified = citationVerifier.verify(generated, evidence);
        verified = new AgentAnswer(consistencyChecker.annotate(evidence, verified.answer()),
                verified.citationIds(), verified.abstained(), verified.confidence());
        long answerDone = System.nanoTime();
        trace.add(step("answer", verified.abstained() ? "abstained" : "ok",
                "引用=" + verified.citationIds(), answerStart, answerDone));

        RunStatus status = verified.abstained() ? RunStatus.ABSTAINED : RunStatus.ANSWERED;
        long finishedNanos = System.nanoTime();
        long durationMs = Math.max(0, (finishedNanos - startedNanos) / 1_000_000);
        trace.add(new TraceStep("verify", "ok",
                "引用校验完成，状态=" + status, Math.max(0, durationMs - 1)));

        String runId = "run_" + UUID.randomUUID();
        AgentRun run = new AgentRun(runId, sessionId, userId, request.question(), plan, status,
                verified.answer(), List.copyOf(evidence), List.copyOf(verified.citationIds()),
                List.copyOf(trace), createdAt, durationMs);
        runs.put(runId, run);
        sessionMemory.update(sessionId, request.question(), scope, runId);
        return toResponse(run, verified.confidence());
    }

    public AgentRun getRun(String userId, String runId) {
        AgentRun run = runs.get(runId);
        if (run == null) throw new NotFoundException("运行记录不存在");
        if (!run.userId().equals(userId)) throw new ForbiddenException("无权查看该运行记录");
        return run;
    }

    private AskResponse toResponse(AgentRun run, double confidence) {
        List<EvidenceView> evidenceViews = run.evidence().stream()
                .map(item -> new EvidenceView(item.evidenceId(), item.projectId(), item.projectName(),
                        item.documentTitle(), item.headingPath(), item.snippet(), item.effectiveDate(), item.finalScore()))
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
        return new AskResponse(run.runId(), run.sessionId(), run.status().name(), run.answer(),
                run.plan().projectIds().stream().toList(), confidence, citationViews, evidenceViews,
                traceViews, run.createdAt());
    }

    private TraceStep step(String stage, String status, String detail, long startNanos, long endNanos) {
        return new TraceStep(stage, status, detail, Math.max(0, (endNanos - startNanos) / 1_000_000));
    }
}
