package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.domain.Domain.Intent;
import com.jaymrli.teamknowledge.service.QueryPlanner;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPlannerTest {
    private final QueryPlanner planner = new QueryPlanner();

    @Test
    void shouldClassifyCoreQuestionIntents() {
        assertThat(planner.plan("当前最大的风险是什么", Set.of("p1")).intent()).isEqualTo(Intent.RISK);
        assertThat(planner.plan("比较两个项目的风险", Set.of("p1", "p2")).intent()).isEqualTo(Intent.COMPARISON);
        assertThat(planner.plan("为什么调整上线时间", Set.of("p1")).intent()).isEqualTo(Intent.DECISION);
        assertThat(planner.plan("当前上线状态如何", Set.of("p1")).intent()).isEqualTo(Intent.STATUS);
    }
}
