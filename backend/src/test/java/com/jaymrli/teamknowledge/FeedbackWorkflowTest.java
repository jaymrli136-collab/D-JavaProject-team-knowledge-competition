package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.FeedbackRequest;
import com.jaymrli.teamknowledge.service.FeedbackService;
import com.jaymrli.teamknowledge.service.KnowledgeAgentHarness;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FeedbackWorkflowTest {
    @Autowired
    private KnowledgeAgentHarness harness;

    @Autowired
    private FeedbackService feedbackService;

    @Test
    void shouldUpdateFeedbackWithoutDuplicatingRun() {
        var answer = harness.ask("u_alice",
                new AskRequest("番茄小说当前最大的风险是什么？", List.of("p_tomato"), "feedback-run"));
        long before = feedbackService.summary().total();
        feedbackService.submit("u_alice", answer.runId(), new FeedbackRequest("HELPFUL", "回答清晰"));
        long afterFirst = feedbackService.summary().total();
        assertThat(afterFirst).isEqualTo(before + 1);

        feedbackService.submit("u_alice", answer.runId(), new FeedbackRequest("NOT_HELPFUL", "希望增加更多数据"));
        assertThat(feedbackService.summary().total()).isEqualTo(afterFirst);
        assertThat(feedbackService.summary().notHelpful()).isGreaterThanOrEqualTo(1);
    }
}
