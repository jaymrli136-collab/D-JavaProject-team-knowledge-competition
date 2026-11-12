package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.Intent;
import com.jaymrli.teamknowledge.domain.Domain.MemoryHint;
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
    public AgentAnswer generate(String question, List<Evidence> evidence, Intent intent, List<MemoryHint> memoryHints) {
        if (evidence.isEmpty()) {
            return new AgentAnswer("知识库中没有找到足够证据。请补充项目文档、会议纪要或决策记录后再试。",
                    List.of(), true, 0.2);
        }

        List<Evidence> selected = evidence.stream().limit(intent == Intent.COMPARISON ? 6 : 4).toList();
        StringBuilder answer = new StringBuilder();
        if (memoryHints != null && !memoryHints.isEmpty()) {
            answer.append("已参考 ").append(memoryHints.size())
                    .append(" 条已确认项目记忆作为软上下文，事实仍以原文引用为准。\n\n");
        }

        if (intent == Intent.COMPARISON) {
            Map<String, List<Evidence>> grouped = selected.stream()
                    .collect(Collectors.groupingBy(Evidence::projectName, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<Evidence>> entry : grouped.entrySet()) {
                answer.append(entry.getKey()).append("：");
                Evidence primary = entry.getValue().get(0);
                answer.append(shorten(primary.snippet())).append(" [").append(primary.evidenceId()).append("]\n");
                entry.getValue().stream().skip(1).limit(1).forEach(item -> answer.append("补充证据：")
                        .append(item.documentTitle()).append(" / ").append(item.headingPath()).append(" [")
                        .append(item.evidenceId()).append("]\n"));
            }
            answer.append("\n以上对比只基于当前可见的 ACTIVE 版本证据。");
        } else {
            Evidence primary = selected.get(0);
            answer.append(switch (intent) {
                case RISK -> "当前最需要关注的是：";
                case STATUS -> "根据当前知识库，项目状态为：";
                case DECISION -> "已记录的相关决策是：";
                case FACT -> "可核实的信息是：";
                default -> "当前知识库中最相关的信息是：";
            });
            answer.append(shorten(primary.snippet())).append(" [").append(primary.evidenceId()).append("]\n");
            answer.append("来源：").append(primary.projectName()).append(" / ")
                    .append(primary.documentTitle()).append(" / ").append(primary.headingPath())
                    .append("（v").append(primary.version()).append("，生效于").append(primary.effectiveDate())
                    .append("）。");
            List<Evidence> supporting = selected.stream().skip(1).limit(2).toList();
            if (!supporting.isEmpty()) {
                answer.append("\n\n补充依据：\n");
                supporting.forEach(item -> answer.append("- ").append(item.documentTitle()).append(" / ")
                        .append(item.headingPath()).append(" [").append(item.evidenceId()).append("]\n"));
            }
            answer.append("\n未在文档中找到支持的信息不会被推断为事实。");
        }

        Set<String> citations = selected.stream().map(Evidence::evidenceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        double average = selected.stream().mapToDouble(Evidence::finalScore).average().orElse(0.0);
        double confidence = Math.min(0.95, 0.55 + average * 0.6 + Math.min(selected.size(), 4) * 0.05);
        return new AgentAnswer(answer.toString(), new ArrayList<>(citations), false,
                Math.round(confidence * 100.0) / 100.0);
    }

    private String shorten(String value) {
        return value.length() <= 240 ? value : value.substring(0, 240) + "...";
    }
}
