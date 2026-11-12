package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TeamKnowledgeApplicationTest {
    @Autowired
    private KnowledgeAgentHarness harness;

    @Test
    void shouldLoadDemoKnowledgeAndAnswerCrossProjectQuestion() {
        AskResponse response = harness.ask("u_alice",
                new AskRequest("比较番茄小说和红果短剧当前最大的风险。", List.of(), "integration"));
        assertThat(response.status()).isEqualTo("ANSWERED");
        assertThat(response.citations()).extracting(item -> item.projectId())
                .contains("p_tomato", "p_duanju");
    }
}
