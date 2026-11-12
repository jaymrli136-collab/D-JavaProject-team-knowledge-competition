package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.RetrievalPreviewRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.service.EvaluationService;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import com.jaymrli.teamknowledge.service.KnowledgeHealthService;
import com.jaymrli.teamknowledge.service.RetrievalPreviewService;
import com.jaymrli.teamknowledge.service.UserDirectoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class TeamKnowledgeApplicationTest {
    @Autowired
    private KnowledgeAgentHarness harness;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private KnowledgeHealthService healthService;

    @Autowired
    private RetrievalPreviewService retrievalPreview;

    @Autowired
    private UserDirectoryService userDirectory;

    @Test
    void shouldLoadDemoKnowledgeAndAnswerCrossProjectQuestion() {
        AskResponse response = harness.ask("u_alice",
                new AskRequest("比较番茄小说和红果短剧当前最大的风险。", List.of(), "integration"));
        assertThat(response.status()).isEqualTo("ANSWERED");
        assertThat(response.citations()).extracting(item -> item.projectId())
                .contains("p_tomato", "p_duanju");
    }

    @Test
    void shouldExposeKnowledgeHealthOverview() {
        var health = healthService.overview("u_alice", null);
        assertThat(health.activeDocuments()).isGreaterThanOrEqualTo(6);
        assertThat(health.supersededDocuments()).isGreaterThanOrEqualTo(1);
        assertThat(health.decisions()).isGreaterThanOrEqualTo(2);
        var projectHealth = healthService.overview("u_alice", "p_tomato");
        assertThat(projectHealth.activeDocuments()).isGreaterThanOrEqualTo(3);
        assertThat(projectHealth.activeDocuments()).isLessThan(health.activeDocuments());
    }

    @Test
    void shouldPreviewRetrievalWithoutGeneratingAnswer() {
        var preview = retrievalPreview.preview("u_alice",
                new RetrievalPreviewRequest("番茄小说当前最大的风险是什么？", List.of("p_tomato")));
        assertThat(preview.intent()).isEqualTo("RISK");
        assertThat(preview.evidence()).isNotEmpty();
        assertThat(preview.projectIds()).containsExactly("p_tomato");
    }

    @Test
    void shouldUploadMarkdownDocument() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "upload-test.md", "text/markdown",
                "# 上传测试\n番茄小说上传文档内容。".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/projects/p_tomato/documents/upload")
                        .file(file)
                        .param("title", "上传接口测试文档")
                        .param("effectiveDate", "2026-11-12")
                        .param("version", "1")
                        .param("lineageKey", "upload-test")
                        .header("X-User-Id", "u_alice"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title")
                        .value("上传接口测试文档"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status")
                        .value("ACTIVE"));
    }

    @Test
    void shouldExposeUserDirectory() {
        assertThat(userDirectory.list()).extracting(item -> item.id())
                .contains("u_alice", "u_bob", "u_carol");
    }

    @Test
    void shouldExposeRuntimeConfigurationWithoutSecrets() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/runtime"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.provider")
                        .value("mock"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.maxEvidence")
                        .value(6))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.apiKey")
                        .doesNotExist());
    }

    @Test
    void shouldExposeKnowledgeHealthIndicator() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.components.knowledge.status").value("UP"));
    }

    @Test
    void shouldRejectMissingUploadFile() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/projects/p_tomato/documents/upload")
                        .param("effectiveDate", "2026-11-12")
                        .header("X-User-Id", "u_alice"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("MISSING_PART"));
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/agent/ask")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("MALFORMED_JSON"));
    }

    @Test
    void shouldSetSecurityHeaders() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .exists("X-Request-Id"));
    }

    @Test
    void shouldExposeEvaluationCases() {
        assertThat(evaluationService.cases()).hasSize(6);
        assertThat(evaluationService.cases()).extracting(item -> item.id())
                .contains("fact-risk", "risk-compare", "abstain", "forbidden");
    }

    @Test
    void shouldPersistEvaluationHistory() {
        var report = evaluationService.runAll();
        var history = evaluationService.history(5);
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).passRate()).isEqualTo(report.passRate());
        assertThat(history.get(0).permissionViolations()).isEqualTo(report.permissionViolations());
        assertThat(report.evidenceRecall()).isGreaterThan(0.0);
    }

    @Test
    void shouldReturnOriginalSectionForCitation() throws Exception {
        AskResponse response = harness.ask("u_alice",
                new AskRequest("番茄小说当前最大的风险是什么？", List.of("p_tomato"), "section-detail"));
        var citation = response.citations().get(0);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/documents/{documentId}/sections/{sectionId}",
                        citation.documentId(), citation.sectionId())
                        .header("X-User-Id", "u_alice"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.documentTitle")
                        .value(citation.documentTitle()));
    }
}
