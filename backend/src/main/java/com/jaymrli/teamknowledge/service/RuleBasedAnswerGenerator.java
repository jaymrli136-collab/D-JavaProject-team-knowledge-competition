package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.Intent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RuleBasedAnswerGenerator implements AnswerGenerator {
    @Override
    public AgentAnswer generate(String question, List<Evidence> evidence, Intent intent) {
        if (evidence.isEmpty()) {
            return new AgentAnswer("知识库中没有找到足够证据。请补充项目文档、会议纪要或决策记录后再试。",
                    List.of(), true, 0.2);
        }

        List<Evidence> selected = evidence.stream().limit(intent == Intent.COMPARISON ? 6 : 4).toList();
        StringBuilder answer = new StringBuilder();
        answer.append("根据团队知识库，当前可核实的相关信息如下：\n\n");

        if (intent == Intent.COMPARISON) {
            Map<String, List<Evidence>> grouped = selected.stream()
                    .collect(Collectors.groupingBy(Evidence::projectName, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<Evidence>> entry : grouped.entrySet()) {
                answer.append("**").append(entry.getKey()).append("**\n");
                entry.getValue().stream().limit(2).forEach(item -> answer.append("- ")
                        .append(shorten(item.snippet())).append(" [").append(item.evidenceId()).append("]\n"));
                answer.append("\n");
            }
            answer.append("以上对比仅基于当前可见文档，跨项目结论需要结合各项目最新版本进一步确认。");
        } else {
            selected.stream().limit(3).forEach(item -> answer.append("- ")
                    .append(item.projectName()).append(" / ").append(item.documentTitle())
                    .append(" / ").append(item.headingPath()).append("：")
                    .append(shorten(item.snippet())).append(" [").append(item.evidenceId()).append("]\n"));
            answer.append("\n以上结论均来自可见项目文档；未命中的信息未被推断。");
        }

        Set<String> citations = selected.stream().map(Evidence::evidenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        double average = selected.stream().mapToDouble(Evidence::finalScore).average().orElse(0.0);
        double confidence = Math.min(0.95, 0.55 + average * 0.6 + Math.min(selected.size(), 4) * 0.05);
        return new AgentAnswer(answer.toString(), new ArrayList<>(citations), false, Math.round(confidence * 100.0) / 100.0);
    }

    private String shorten(String value) {
        return value.length() <= 220 ? value : value.substring(0, 220) + "...";
    }
}
