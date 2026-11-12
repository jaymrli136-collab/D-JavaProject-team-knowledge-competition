package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import com.jaymrli.teamknowledge.domain.Domain.Intent;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeSection;
import com.jaymrli.teamknowledge.domain.Domain.Project;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class HybridRetriever {
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Set<String> STOP_TOKENS = Set.of(
            "什么", "多少", "怎么", "为什么", "如何", "当前", "现在", "请问", "一下", "这个", "那个",
            "项目", "时候", "哪些", "是否", "可以", "的", "了", "是", "在", "有", "和", "与"
    );

    private final KnowledgeStore store;
    private final TextAnalyzer analyzer;

    public HybridRetriever(KnowledgeStore store, TextAnalyzer analyzer) {
        this.store = store;
        this.analyzer = analyzer;
    }

    public List<Evidence> retrieve(String query, Set<String> projectIds, Intent intent, int limit) {
        List<KnowledgeDocument> documents = store.documentsForProjects(projectIds);
        if (documents.isEmpty()) return List.of();

        List<Candidate> candidates = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            Project project = store.findProject(document.projectId()).orElseThrow();
            for (KnowledgeSection section : document.sections()) {
                String searchable = document.title() + " " + document.l0Summary() + " " + document.l1Overview()
                        + " " + section.headingPath() + " " + section.content();
                candidates.add(new Candidate(project, document, section,
                        analyzer.termFrequency(searchable), analyzer.tokenize(searchable).size(),
                        analyzer.vectorize(searchable)));
            }
        }

        String expandedQuery = query + " " + intentTerms(intent);
        List<String> queryTokens = analyzer.tokenize(expandedQuery).stream().distinct().toList();
        if (queryTokens.isEmpty()) return List.of();
        List<String> anchorTokens = analyzer.tokenize(query).stream().distinct().filter(this::isAnchorToken).toList();

        double averageLength = candidates.stream().mapToInt(Candidate::length).average().orElse(1.0);
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String token : queryTokens) {
            int frequency = 0;
            for (Candidate candidate : candidates) {
                if (candidate.termFrequency().containsKey(token)) frequency++;
            }
            documentFrequency.put(token, frequency);
        }

        double[] queryVector = analyzer.vectorize(expandedQuery);
        List<ScoredCandidate> scored = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!anchorTokens.isEmpty() && !containsAnchor(candidate, anchorTokens)) continue;
            double bm25 = bm25(candidate, queryTokens, documentFrequency, averageLength, candidates.size());
            double vector = analyzer.cosine(queryVector, candidate.vector());
            double bonus = rerankBonus(query, candidate);
            double combined = Math.min(1.0, 0.62 * bm25 + 0.30 * vector + bonus);
            if (combined > 0.015) scored.add(new ScoredCandidate(candidate, bm25, vector, combined));
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::combined).reversed());
        List<ScoredCandidate> selected = ensureProjectCoverage(scored, limit);
        List<Evidence> evidence = new ArrayList<>();
        int index = 1;
        for (ScoredCandidate item : selected) {
            Candidate candidate = item.candidate();
            evidence.add(new Evidence(
                    "E" + index++, candidate.project().id(), candidate.project().name(),
                    candidate.document().id(), candidate.document().title(),
                    candidate.section().id(), candidate.section().headingPath(),
                    analyzer.snippet(candidate.section().content()), candidate.document().sourceUri(),
                    candidate.document().version(), candidate.document().effectiveDate(),
                    round(item.bm25()), round(item.vector()), round(item.combined())
            ));
        }
        return evidence;
    }

    private List<ScoredCandidate> ensureProjectCoverage(List<ScoredCandidate> scored, int limit) {
        if (scored.isEmpty()) return List.of();
        Map<String, ScoredCandidate> bestByProject = new LinkedHashMap<>();
        for (ScoredCandidate candidate : scored) bestByProject.putIfAbsent(candidate.candidate().project().id(), candidate);
        List<ScoredCandidate> selected = new ArrayList<>();
        if (bestByProject.size() > 1) selected.addAll(bestByProject.values());
        for (ScoredCandidate candidate : scored) {
            if (selected.size() >= limit) break;
            if (!selected.contains(candidate)) selected.add(candidate);
        }
        return selected.stream().limit(limit).toList();
    }

    private double bm25(Candidate candidate, List<String> queryTokens,
                        Map<String, Integer> documentFrequency, double averageLength, int totalCandidates) {
        double score = 0.0;
        for (String token : queryTokens) {
            int tf = candidate.termFrequency().getOrDefault(token, 0);
            if (tf == 0) continue;
            int df = documentFrequency.getOrDefault(token, 0);
            double idf = Math.log(1.0 + (totalCandidates - df + 0.5) / (df + 0.5));
            double denominator = tf + K1 * (1.0 - B + B * candidate.length() / Math.max(1.0, averageLength));
            score += idf * (tf * (K1 + 1.0)) / denominator;
        }
        return Math.min(1.0, score / Math.max(1.0, queryTokens.size() * 2.2));
    }

    private double rerankBonus(String query, Candidate candidate) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String title = candidate.document().title().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String heading = candidate.section().headingPath().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String project = candidate.project().name().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        double bonus = 0.0;
        if (!normalizedQuery.isBlank() && title.contains(normalizedQuery)) bonus += 0.12;
        if (!normalizedQuery.isBlank() && heading.contains(normalizedQuery)) bonus += 0.08;
        if (!normalizedQuery.isBlank() && normalizedQuery.contains(project)) bonus += 0.05;
        long ageDays = Math.abs(ChronoUnit.DAYS.between(candidate.document().effectiveDate(), LocalDate.now()));
        if (ageDays <= 30) bonus += 0.04;
        else if (ageDays <= 180) bonus += 0.02;
        return bonus;
    }

    private String intentTerms(Intent intent) {
        return switch (intent) {
            case RISK -> "风险 问题 阻塞 成本 合规";
            case DECISION -> "决策 结论 原因 依据";
            case STATUS -> "状态 当前 进展 上线 时间";
            case COMPARISON -> "比较 差异 共同点 风险 目标";
            case FACT -> "事实 指标 数据";
            case UNKNOWN -> "";
        };
    }

    private boolean isAnchorToken(String token) {
        if (STOP_TOKENS.contains(token)) return false;
        return token.length() > 1 || token.codePoints().allMatch(code -> code < 128);
    }

    private boolean containsAnchor(Candidate candidate, List<String> anchorTokens) {
        Set<String> projectTokens = Set.copyOf(analyzer.tokenize(candidate.project().name()));
        List<String> contentAnchors = anchorTokens.stream()
                .filter(token -> !projectTokens.contains(token))
                .toList();
        if (contentAnchors.isEmpty()) return true;
        return contentAnchors.stream().anyMatch(candidate.termFrequency()::containsKey);
    }

    private double round(double value) { return Math.round(value * 10000.0) / 10000.0; }

    private record Candidate(Project project, KnowledgeDocument document, KnowledgeSection section,
                             Map<String, Integer> termFrequency, int length, double[] vector) {}
    private record ScoredCandidate(Candidate candidate, double bm25, double vector, double combined) {}
}
