package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.AgentAnswer;
import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CitationVerifier {
    private static final Pattern CITATION = Pattern.compile("\\[(E\\d+)]");

    public AgentAnswer verify(AgentAnswer answer, List<Evidence> evidence) {
        if (answer == null || answer.abstained() || evidence.isEmpty()) return answer;
        Set<String> validIds = evidence.stream().map(Evidence::evidenceId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> used = new LinkedHashSet<>();
        Matcher matcher = CITATION.matcher(answer.answer());
        StringBuffer cleaned = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group(1);
            if (validIds.contains(id)) {
                used.add(id);
                matcher.appendReplacement(cleaned, Matcher.quoteReplacement("[" + id + "]"));
            } else {
                matcher.appendReplacement(cleaned, "");
            }
        }
        matcher.appendTail(cleaned);

        if (answer.citationIds() != null) {
            for (String id : answer.citationIds()) if (validIds.contains(id)) used.add(id);
        }
        if (used.isEmpty()) {
            evidence.stream().limit(2).map(Evidence::evidenceId).forEach(used::add);
            cleaned.append("\n\n可核对证据：");
            used.forEach(id -> cleaned.append(" [").append(id).append(']'));
        }
        String text = cleaned.toString().trim();
        if (text.isBlank()) return new AgentAnswer("证据校验失败，暂时无法生成可信回答。", List.of(), true, 0.1);
        return new AgentAnswer(text, new ArrayList<>(used), false, answer.confidence());
    }
}
