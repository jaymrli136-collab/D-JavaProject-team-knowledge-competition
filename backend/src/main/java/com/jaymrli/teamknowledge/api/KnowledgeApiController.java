package com.jaymrli.teamknowledge.api;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.CreateProjectRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.DocumentDiffView;
import com.jaymrli.teamknowledge.api.ApiDtos.DocumentView;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationCaseView;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationReport;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationSnapshotView;
import com.jaymrli.teamknowledge.api.ApiDtos.FeedbackRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.FeedbackSummary;
import com.jaymrli.teamknowledge.api.ApiDtos.FeedbackView;
import com.jaymrli.teamknowledge.api.ApiDtos.IngestDocumentRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.KnowledgeGapView;
import com.jaymrli.teamknowledge.api.ApiDtos.KnowledgeHealthView;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryProposalRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryView;
import com.jaymrli.teamknowledge.api.ApiDtos.ProjectArchiveView;
import com.jaymrli.teamknowledge.api.ApiDtos.ProjectView;
import com.jaymrli.teamknowledge.api.ApiDtos.ResolveGapRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.RetrievalPreviewRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.RetrievalPreviewView;
import com.jaymrli.teamknowledge.api.ApiDtos.RuntimeConfigView;
import com.jaymrli.teamknowledge.api.ApiDtos.RunMetricsView;
import com.jaymrli.teamknowledge.api.ApiDtos.RunSummaryView;
import com.jaymrli.teamknowledge.api.ApiDtos.SectionDetailView;
import com.jaymrli.teamknowledge.api.ApiDtos.UpdateMembersRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.UpdateRoleRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.UserView;
import com.jaymrli.teamknowledge.domain.Domain.AgentRun;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.service.AccessControlService;
import com.jaymrli.teamknowledge.service.DocumentIngestionService;
import com.jaymrli.teamknowledge.service.DocumentDiffService;
import com.jaymrli.teamknowledge.service.DocumentParserService;
import com.jaymrli.teamknowledge.service.EvaluationService;
import com.jaymrli.teamknowledge.service.FeedbackService;
import com.jaymrli.teamknowledge.service.NotFoundException;
import com.jaymrli.teamknowledge.service.AgentStreamService;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import com.jaymrli.teamknowledge.service.KnowledgeGapService;
import com.jaymrli.teamknowledge.service.KnowledgeHealthService;
import com.jaymrli.teamknowledge.service.ProjectArchiveService;
import com.jaymrli.teamknowledge.service.ProjectManagementService;
import com.jaymrli.teamknowledge.service.ProjectMemoryService;
import com.jaymrli.teamknowledge.service.ProjectRoleService;
import com.jaymrli.teamknowledge.service.RetrievalPreviewService;
import com.jaymrli.teamknowledge.service.UserDirectoryService;
import com.jaymrli.teamknowledge.service.WorkspacePersistenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
    private final KnowledgeGapService gapService;
    private final AgentStreamService streamService;
    private final KnowledgeHealthService healthService;
    private final FeedbackService feedbackService;
    private final UserDirectoryService userDirectory;
    private final ProjectManagementService projectManagement;
    private final RetrievalPreviewService retrievalPreview;
    private final DocumentParserService documentParser;
    private final DocumentDiffService documentDiff;
    private final TeamKnowledgeProperties properties;
    private final ProjectArchiveService projectArchive;
    private final WorkspacePersistenceService persistenceService;
    private final ProjectRoleService roleService;

    public KnowledgeApiController(KnowledgeStore store, AccessControlService accessControl,
                                  DocumentIngestionService ingestion, KnowledgeAgentHarness harness,
                                  EvaluationService evaluation, ProjectMemoryService memoryService,
                                  KnowledgeGapService gapService, AgentStreamService streamService,
                                  KnowledgeHealthService healthService, FeedbackService feedbackService,
                                  UserDirectoryService userDirectory,
                                  ProjectManagementService projectManagement,
                                  RetrievalPreviewService retrievalPreview, DocumentParserService documentParser,
                                  DocumentDiffService documentDiff, TeamKnowledgeProperties properties,
                                  ProjectArchiveService projectArchive,
                                  WorkspacePersistenceService persistenceService, ProjectRoleService roleService) {
        this.store = store;
        this.accessControl = accessControl;
        this.ingestion = ingestion;
        this.harness = harness;
        this.evaluation = evaluation;
        this.memoryService = memoryService;
        this.gapService = gapService;
        this.streamService = streamService;
        this.healthService = healthService;
        this.feedbackService = feedbackService;
        this.userDirectory = userDirectory;
        this.projectManagement = projectManagement;
        this.retrievalPreview = retrievalPreview;
        this.documentParser = documentParser;
        this.documentDiff = documentDiff;
        this.properties = properties;
        this.projectArchive = projectArchive;
        this.persistenceService = persistenceService;
        this.roleService = roleService;
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

    @PostMapping("/persistence/save")
    public Map<String, Object> savePersistence() {
        persistenceService.save();
        return Map.of("saved", true);
    }

    @GetMapping("/runtime")
    public RuntimeConfigView runtime() {
        TeamKnowledgeProperties.Llm llm = properties.llm();
        boolean realEmbedding = "openai".equalsIgnoreCase(llm.provider())
                && llm.apiKey() != null && !llm.apiKey().isBlank();
        return new RuntimeConfigView(llm.provider(), llm.model(), llm.embeddingModel(), realEmbedding,
                properties.retrieval().maxEvidence(), properties.retrieval().maxRunMillis(), "project-atlas-v1");
    }

    @GetMapping("/health/overview")
    public KnowledgeHealthView healthOverview(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                              @RequestParam(required = false) String projectId) {
        return healthService.overview(userId, projectId);
    }

    @GetMapping("/users")
    public List<UserView> users() {
        return userDirectory.list();
    }

    @GetMapping("/projects")
    public List<ProjectView> projects(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId) {
        return accessControl.visibleProjects(userId).stream()
                .map(project -> new ProjectView(project.id(), project.name(), project.description(),
                        project.ownerId(), project.status(), project.memberIds(), roleService.rolesForProject(project),
                        (int) store.documentCount(project.id()),
                        (int) store.decisionCount(project.id())))
                .toList();
    }

    @PostMapping("/projects")
    public ProjectView createProject(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                     @Valid @RequestBody CreateProjectRequest request) {
        return projectManagement.create(userId, request);
    }

    @PostMapping("/projects/{projectId}/roles")
    public Map<String, String> updateRole(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @PathVariable String projectId,
            @Valid @RequestBody UpdateRoleRequest request) {
        var role = roleService.setRole(userId, projectId, request.memberId(), request.role());
        return Map.of("projectId", projectId, "memberId", request.memberId(), "role", role.name());
    }

    @PostMapping("/projects/{projectId}/members")
    public ProjectView updateProjectMembers(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                            @PathVariable String projectId,
                                            @Valid @RequestBody UpdateMembersRequest request) {
        return projectManagement.updateMembers(userId, projectId, request.memberIds());
    }

    @GetMapping("/projects/{projectId}/archive")
    public ProjectArchiveView exportProject(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @PathVariable String projectId) {
        return projectArchive.exportProject(userId, projectId);
    }

    @PostMapping("/projects/archive/import")
    public ProjectArchiveView importProject(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @RequestBody ProjectArchiveView archive) {
        return projectArchive.importProject(userId, archive);
    }

    @GetMapping("/projects/{projectId}/documents")
    public List<DocumentView> documents(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                        @PathVariable String projectId) {
        accessControl.requireAccessibleProject(userId, projectId);
        return store.documentsForProjects(Set.of(projectId)).stream().map(this::toView).toList();
    }

    @PostMapping("/documents/{documentId}/restore")
    public DocumentView restoreDocument(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @PathVariable String documentId) {
        KnowledgeDocument document = store.findDocument(documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + documentId));
        accessControl.requireAccessibleProject(userId, document.projectId());
        roleService.requireEditor(userId, document.projectId());
        store.restoreDocument(documentId);
        return toView(document);
    }

    @GetMapping("/documents/{documentId}/diff/{otherDocumentId}")
    public DocumentDiffView documentDiff(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @PathVariable String documentId, @PathVariable String otherDocumentId) {
        return documentDiff.diff(userId, documentId, otherDocumentId);
    }

    @PostMapping("/documents/{documentId}/archive")
    public DocumentView archiveDocument(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @PathVariable String documentId) {
        KnowledgeDocument document = store.findDocument(documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + documentId));
        accessControl.requireAccessibleProject(userId, document.projectId());
        roleService.requireEditor(userId, document.projectId());
        store.archiveDocument(documentId);
        return toView(document);
    }

    @GetMapping("/documents/{documentId}/history")
    public List<DocumentView> documentHistory(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                              @PathVariable String documentId) {
        KnowledgeDocument document = store.findDocument(documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + documentId));
        accessControl.requireAccessibleProject(userId, document.projectId());
        return store.historyFor(documentId).stream().map(this::toView).toList();
    }

    @PostMapping(value = "/projects/{projectId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentView uploadDocument(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @PathVariable String projectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam String effectiveDate,
            @RequestParam(defaultValue = "1") Integer version,
            @RequestParam(required = false) String lineageKey) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        try {
            String filename = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();
            String content = documentParser.extract(filename, file.getBytes());
            String documentTitle = title == null || title.isBlank()
                    ? filename.replaceFirst("\\.[^.]+$", "") : title;
            return toView(ingestion.ingest(userId, projectId, new IngestDocumentRequest(
                    documentTitle, content, "upload://" + filename, java.time.LocalDate.parse(effectiveDate),
                    version, java.util.Set.of("upload"), lineageKey)));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("读取上传文件失败", ex);
        }
    }

    @GetMapping("/documents/{documentId}/sections/{sectionId}")
    public SectionDetailView sectionDetail(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                           @PathVariable String documentId,
                                           @PathVariable String sectionId) {
        KnowledgeDocument document = store.findDocument(documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + documentId));
        accessControl.requireAccessibleProject(userId, document.projectId());
        var section = document.sections().stream().filter(item -> item.id().equals(sectionId)).findFirst()
                .orElseThrow(() -> new NotFoundException("章节不存在: " + sectionId));
        Project project = store.findProject(document.projectId()).orElseThrow();
        return new SectionDetailView(document.projectId(), project.name(), document.id(), document.title(),
                section.id(), section.headingPath(), section.content(), document.sourceUri(), document.version(),
                document.effectiveDate(), store.status(document.id()).name(), store.supersededBy(document.id()));
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

    @PostMapping(value = "/agent/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                @Valid @RequestBody AskRequest request) {
        return streamService.stream(userId, request);
    }

    @GetMapping("/runs/metrics")
    public RunMetricsView runMetrics(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId) {
        return harness.metricsView(userId);
    }

    @GetMapping("/runs")
    public List<RunSummaryView> runs(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                     @RequestParam(defaultValue = "20") int limit) {
        return harness.recentRuns(userId, Math.min(100, Math.max(1, limit)));
    }

    @PostMapping("/runs/{runId}/feedback")
    public FeedbackView submitFeedback(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                       @PathVariable String runId,
                                       @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.submit(userId, runId, request);
    }

    @GetMapping("/feedback/summary")
    public FeedbackSummary feedbackSummary() {
        return feedbackService.summary();
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

    @GetMapping("/gaps")
    public List<KnowledgeGapView> gaps(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                       @RequestParam(required = false) String projectId) {
        return gapService.listVisible(userId, projectId);
    }

    @GetMapping(value = "/gaps/export", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportGaps(
            @RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
            @RequestParam(required = false) String projectId) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=knowledge-gaps.md")
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(gapService.exportMarkdown(userId, projectId));
    }

    @PostMapping("/gaps/{gapId}/resolve")
    public KnowledgeGapView resolveGap(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                       @PathVariable String gapId,
                                       @RequestBody(required = false) ResolveGapRequest request) {
        return gapService.resolve(userId, gapId, request == null ? null : request.note());
    }

    @PostMapping("/gaps/{gapId}/ignore")
    public KnowledgeGapView ignoreGap(@RequestHeader(value = "X-User-Id", defaultValue = "u_alice") String userId,
                                      @PathVariable String gapId,
                                      @RequestBody(required = false) ResolveGapRequest request) {
        return gapService.ignore(userId, gapId, request == null ? null : request.note());
    }

    @PostMapping("/evaluations/run")
    public EvaluationReport evaluate() {
        return evaluation.runAll();
    }

    @GetMapping("/evaluations/cases")
    public List<EvaluationCaseView> evaluationCases() {
        return evaluation.cases();
    }

    @GetMapping("/evaluations/history")
    public List<EvaluationSnapshotView> evaluationHistory(@RequestParam(defaultValue = "10") int limit) {
        return evaluation.history(Math.min(50, Math.max(1, limit)));
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
                document.l0Summary(), document.sections().size(), document.lineageKey(),
                store.status(document.id()).name(), store.supersededBy(document.id()));
    }
}
