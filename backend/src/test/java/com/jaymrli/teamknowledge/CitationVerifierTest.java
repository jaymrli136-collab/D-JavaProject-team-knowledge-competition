package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.service.CitationVerifier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationVerifierTest {
    private final CitationVerifier verifier = new CitationVerifier();

    @Test
    void shouldRemoveInvalidCitationAndKeepValidOne() {
        AgentAnswer answer = new AgentAnswer("风险是内容成本上涨 [E1]，错误引用 [E99] 已移除。",
                List.of("E1", "E99"), false, 0.8);
        AgentAnswer verified = verifier.verify(answer, List.of(evidence("E1")));
        assertThat(verified.answer()).contains("[E1]").doesNotContain("[E99]");
        assertThat(verified.citationIds()).containsExactly("E1");
        assertThat(verifier.coverage(verified.answer())).isEqualTo(1.0);
    }

    @Test
    void shouldWarnWhenSubstantiveCoverageIsLow() {
        AgentAnswer answer = new AgentAnswer("第一项结论没有引用支撑。\n第二项结论同样没有引用支撑。",
                List.of(), false, 0.6);
        AgentAnswer verified = verifier.verify(answer, List.of(evidence("E1")));
        assertThat(verified.answer()).contains("引用覆盖提示");
        assertThat(verified.citationIds()).contains("E1");
    }

    private Evidence evidence(String id) {
        return new Evidence(id, "p1", "测试项目", "d1", "风险文档", "s1", "风险",
                "内容成本上涨。", "https://docs.example.com/risk", 1,
                LocalDate.of(2026, 11, 1), 0.8, 0.7, 0.9);
    }
}
