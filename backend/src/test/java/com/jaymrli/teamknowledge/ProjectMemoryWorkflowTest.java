package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryProposalRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.MemoryView;
import com.jaymrli.teamknowledge.service.ForbiddenException;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import com.jaymrli.teamknowledge.service.ProjectMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProjectMemoryWorkflowTest {
    @Autowired
    private KnowledgeAgentHarness harness;

    @Autowired
    private ProjectMemoryService memoryService;

    @Test
    void shouldGovernMemoryFromGeneratedEvidence() {
        AskResponse answer = harness.ask("u_alice",
                new AskRequest("番茄小说增长实验当前最大的风险是什么？", List.of("p_tomato"), "memory-flow"));
        assertThat(answer.citations()).isNotEmpty();

        MemoryView proposed = memoryService.propose("u_alice", new MemoryProposalRequest(
                "p_tomato", "RISK", "番茄小说内容成本风险",
                "短剧内容采购成本连续上涨，需要控制低效采买。",
                answer.runId(), List.of(answer.citations().get(0).evidenceId())
        ));
        assertThat(proposed.status()).isEqualTo("PROPOSED");
        assertThat(proposed.sourceRunId()).isEqualTo(answer.runId());
        assertThat(proposed.sourceEvidenceIds()).isNotEmpty();

        MemoryView active = memoryService.confirm("u_bob", proposed.id());
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(active.confirmedBy()).isEqualTo("u_bob");

        AskResponse memoryAware = harness.ask("u_alice",
                new AskRequest("内容成本风险现在怎么处理？", List.of("p_tomato"), "memory-flow-2"));
        assertThat(memoryAware.memoryHints()).extracting(item -> item.memoryId()).contains(proposed.id());

        MemoryView retired = memoryService.retire("u_bob", proposed.id());
        assertThat(retired.status()).isEqualTo("RETIRED");
        assertThat(retired.retiredBy()).isEqualTo("u_bob");
    }

    @Test
    void shouldRejectMemoryForUnauthorizedProject() {
        assertThatThrownBy(() -> memoryService.listVisible("u_alice", "p_toutiao"))
                .isInstanceOf(ForbiddenException.class);
    }
}
