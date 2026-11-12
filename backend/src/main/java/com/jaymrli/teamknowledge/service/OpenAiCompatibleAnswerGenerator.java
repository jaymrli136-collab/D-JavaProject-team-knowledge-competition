package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.Intent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Primary
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "openai")
public class OpenAiCompatibleAnswerGenerator implements AnswerGenerator {
    private static final Pattern CITATION = Pattern.compile("\\[E(\\d+)]");
    private final TeamKnowledgeProperties properties;
    private final RuleBasedAnswerGenerator fallback;
    private final RestClient client;

    public OpenAiCompatibleAnswerGenerator(TeamKnowledgeProperties properties,
                                           RuleBasedAnswerGenerator fallback,
                                           RestClient.Builder builder) {
        this.properties = properties;
        this.fallback = fallback;
        this.client = builder.baseUrl(properties.llm().baseUrl()).build();
    }

    @Override
    public AgentAnswer generate(String question, List<Evidence> evidence, Intent intent) {
        if (evidence.isEmpty()) return fallback.generate(question, evidence, intent);
        try {
            StringBuilder context = new StringBuilder();
            for (Evidence item : evidence) {
                context.append('[').append(item.evidenceId()).append("] ")
                        .append(item.projectName()).append(" / ").append(item.documentTitle())
                        .append(" / ").append(item.headingPath()).append('\n')
                        .append(item.snippet()).append("\n\n");
            }
            String system = "你是团队知识问答 Agent。只能使用给定证据回答，每个关键结论后必须标注 [E1] 形式引用。"
                    + "证据不足时明确拒答。比较问题按项目组织。不要输出证据之外的事实。";
            String user = "问题：" + question + "\n\n证据：\n" + context;
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", properties.llm().model());
            request.put("temperature", properties.llm().temperature());
            request.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)
            ));
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.llm().apiKey())
                    .body(request).retrieve().body(Map.class);
            String content = extractContent(response);
            if (content == null || content.isBlank()) return fallback.generate(question, evidence, intent);
            List<String> citationIds = new java.util.ArrayList<>();
            Matcher matcher = CITATION.matcher(content);
            while (matcher.find()) citationIds.add("E" + matcher.group(1));
            return new AgentAnswer(content, citationIds, false, 0.78);
        } catch (RuntimeException ex) {
            return fallback.generate(question, evidence, intent);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) return null;
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) return null;
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) return null;
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) return null;
        Object content = message.get("content");
        return content == null ? null : String.valueOf(content);
    }
}
