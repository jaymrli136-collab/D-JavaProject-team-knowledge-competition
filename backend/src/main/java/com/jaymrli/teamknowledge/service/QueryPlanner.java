package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.Intent;
import com.jaymrli.teamknowledge.domain.Domain.Plan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class QueryPlanner {
    public Plan plan(String question, Set<String> projectIds) {
        String normalized = question.toLowerCase(Locale.ROOT);
        Intent intent;
        if (containsAny(normalized, "比较", "对比", "差异", "区别", "共同点", " vs ")) intent = Intent.COMPARISON;
        else if (containsAny(normalized, "风险", "问题", "阻塞", "隐患", "成本")) intent = Intent.RISK;
        else if (containsAny(normalized, "决策", "为什么", "原因", "结论", "依据")) intent = Intent.DECISION;
        else if (containsAny(normalized, "状态", "进展", "上线", "什么时候", "时间", "当前")) intent = Intent.STATUS;
        else if (containsAny(normalized, "指标", "数据", "多少", "是什么", "定义")) intent = Intent.FACT;
        else intent = Intent.UNKNOWN;

        List<String> queries = switch (intent) {
            case RISK -> List.of(question, question + " 风险 阻塞 成本 合规");
            case DECISION -> List.of(question, question + " 决策 结论 依据");
            case STATUS -> List.of(question, question + " 当前状态 时间 负责人");
            case COMPARISON -> List.of(question, question + " 对比 目标 风险 差异");
            case FACT -> List.of(question, question + " 指标 数据 定义");
            case UNKNOWN -> List.of(question);
        };
        return new Plan(intent, queries, Set.copyOf(projectIds), "根据问题关键词和项目权限生成查询计划");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
