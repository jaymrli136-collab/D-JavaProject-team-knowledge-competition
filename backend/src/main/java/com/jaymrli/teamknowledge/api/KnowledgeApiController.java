package com.jaymrli.teamknowledge.api;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.DocumentView;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationReport;
import com.jaymrli.teamknowledge.api.ApiDtos.IngestDocumentRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryProposalRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryView;
import com.jaymrli.teamknowledge.api.ApiDtos.ProjectView;
import com.jaymrli.teamknowledge.domain.Domain.AgentRun;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.service.AccessControlService;
import com.jaymrli.teamknowledge.service.DocumentIngestionService;
import com.jaymrli.teamknowledge.service.EvaluationService;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import com.jaymrli.teamknowledge.service.ProjectMemoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class KnowledgeApiController {
    private final KnowledgeStore store;
    private final AccessControlService accessControl;
    private final DocumentIngestionService ingestion;
    private final KnowledgeAgentHarness harness;
    private final EvaluationService evaluation;
    private final ProjectMemoryService memoryService;

    public KnowledgeApiController(KnowledgeStore store, AccessControlService accessControl,
                                  DocumentIngestionService ingestion, KnowledgeAgentHarness harness,
                                  EvaluationService evaluation, ProjectMemoryService memoryService) {
        this.store = store;
        this.accessControl = accessControl;
        this.ingestion = ingestion;
        this.harness = harness;
        this.evaluation = evaluation;
        this.memoryService = memoryService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "team-knowledge-competition",
                "projects", store.allProjects().size(), "documents", store.allDocuments().size());
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId) {
        return Map.of("userId", userId,
                "visibleProjectIds", accessControl.visibleProjects(userId).stream().map(Project::id).toList());
    }

    @GetMapping("/projects")
    public List<ProjectView> projects(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId) {
        return accessControl.visibleProjects(userId).stream()
                .map(project -> new ProjectView(project.id(), project.name(), project.description(),
                        project.ownerId(), project.status(), (int) store.documentCount(project.id()),
                        (int) store.decisionCount(project.id())))
                .toList();
    }

    @GetMapping("/projects/{projectId}/documents")
    public List<DocumentView> documents(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                        @PathVariable String projectId) {
        accessControl.requireAccessibleProject(userId, projectId);
        return store.documentsForProjects(Set.of(projectId)).stream().map(this::toView).toList();
    }

    @PostMapping("/projects/{projectId}/documents")
    public DocumentView ingest(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                               @PathVariable String projectId,
                               @Valid @RequestBody IngestDocumentRequest request) {
        return toView(ingestion.ingest(userId, projectId, request));
    }

    @PostMapping("/agent/ask")
    public AskResponse ask(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                           @Valid @RequestBody AskRequest request) {
        return harness.ask(userId, request);
    }

    @GetMapping("/runs/{runId}")
    public AgentRun run(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                        @PathVariable String runId) {
        return harness.getRun(userId, runId);
    }

    @GetMapping("/memories")
    public List<MemoryView> memories(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                     @RequestParam(required = false) String projectId) {
        return memoryService.listVisible(userId, projectId);
    }

    @PostMapping("/memories/propose")
    public MemoryView proposeMemory(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                    @Valid @RequestBody MemoryProposalRequest request) {
        return memoryService.propose(userId, request);
    }

    @PostMapping("/memories/{memoryId}/confirm")
    public MemoryView confirmMemory(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                    @PathVariable String memoryId) {
        return memoryService.confirm(userId, memoryId);
    }

    @PostMapping("/memories/{memoryId}/retire")
    public MemoryView retireMemory(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                   @PathVariable String memoryId) {
        return memoryService.retire(userId, memoryId);
    }

    @PostMapping("/evaluations/run")
    public EvaluationReport evaluate() {
        return evaluation.runAll();
    }

    @GetMapping("/demo/questions")
    public List<Map<String, Object>> demoQuestions() {
        return List.of(
                Map.of("label", "番茄小说当前最大风险", "question", "番茄小说增长实验当前最大的风险是什么？",
                        "projectIds", List.of("p_tomato")),
                Map.of("label", "跨项目风险对比", "question", "比较番茄小说和红果短剧当前最大的风险。",
                        "projectIds", List.of("p_tomato", "p_duanju")),
                Map.of("label", "历史决策", "question", "红果短剧为什么限制同类内容连续推荐？",
                        "projectIds", List.of("p_duanju")),
                Map.of("label", "会话追问", "question", "那它当前最大的风险是什么？", "projectIds", List.of()),
                Map.of("label", "无答案测试", "question", "番茄小说在欧洲市场的覆盖率是多少？",
                        "projectIds", List.of("p_tomato")),
                Map.of("label", "越权测试", "question", "头条热点专题当前最大的风险是什么？",
                        "projectIds", List.of())
        );
    }

    private DocumentView toView(KnowledgeDocument document) {
        return new DocumentView(document.id(), document.projectId(), document.title(), document.sourceType(),
                document.sourceUri(), document.version(), document.effectiveDate(), document.tags(),
                document.l0Summary(), document.sections().size());
    }
}
