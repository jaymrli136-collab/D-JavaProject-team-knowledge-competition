package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationCaseResult;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationCaseView;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationReport;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationSnapshotView;
import com.jaymrli.teamknowledge.domain.Domain.EvaluationSnapshot;
import com.jaymrli.teamknowledge.repository.EvaluationHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EvaluationService {
    private final KnowledgeAgentHarness harness;
    private final EvaluationHistoryRepository historyRepository;

    public EvaluationService(KnowledgeAgentHarness harness, EvaluationHistoryRepository historyRepository) {
        this.harness = harness;
        this.historyRepository = historyRepository;
    }

    public EvaluationReport runAll() {
        List<EvalCase> cases = casesDefinitions();
        List<EvaluationCaseResult> results = new ArrayList<>();
        for (EvalCase testCase : cases) results.add(runCase(testCase));
        int passed = (int) results.stream().filter(EvaluationCaseResult::passed).count();
        long answered = results.stream().filter(item -> "ANSWERED".equals(item.actualStatus())).count();
        long cited = results.stream().filter(item -> "ANSWERED".equals(item.actualStatus()) && item.passed()).count();
        Set<String> evidenceCases = cases.stream().filter(item -> !item.evidenceKeywords().isEmpty())
                .map(EvalCase::id).collect(java.util.stream.Collectors.toSet());
        long expectedEvidence = results.stream().filter(item -> evidenceCases.contains(item.id())).count();
        long evidenceMatched = results.stream().filter(item -> evidenceCases.contains(item.id()))
                .filter(EvaluationCaseResult::evidenceMatched).count();
        long expectedAbstain = cases.stream().filter(item -> "ABSTAINED".equals(item.expected())).count();
        long correctAbstain = results.stream().filter(item -> "ABSTAINED".equals(item.expected())
                && "ABSTAINED".equals(item.actualStatus())).count();
        int permissionViolations = (int) results.stream().filter(item -> "FORBIDDEN".equals(item.expected())
                && !"FORBIDDEN".equals(item.actualStatus())).count();
        EvaluationSnapshot snapshot = new EvaluationSnapshot("eval_" + java.util.UUID.randomUUID(), Instant.now(),
                results.size(), passed, ratio(passed, results.size()), ratio(cited, answered),
                ratio(evidenceMatched, expectedEvidence), ratio(correctAbstain, expectedAbstain), permissionViolations);
        historyRepository.save(snapshot);
        return new EvaluationReport(results.size(), passed, snapshot.passRate(), snapshot.citationCoverage(),
                snapshot.evidenceRecall(), snapshot.abstentionAccuracy(), snapshot.permissionViolations(), results);
    }

    public List<EvaluationCaseView> cases() {
        return casesDefinitions().stream()
                .map(item -> new EvaluationCaseView(item.id(), item.question(), item.userId(), item.expected(),
                        item.keywords(), item.evidenceKeywords(), item.requiredProjectIds()))
                .toList();
    }

    public List<EvaluationSnapshotView> history(int limit) {
        return historyRepository.recent(limit).stream()
                .map(item -> new EvaluationSnapshotView(item.id(), item.createdAt(), item.total(), item.passed(),
                        item.passRate(), item.citationCoverage(), item.evidenceRecall(),
                        item.abstentionAccuracy(), item.permissionViolations()))
                .toList();
    }

    private List<EvalCase> casesDefinitions() {
        return List.of(
                new EvalCase("fact-risk", "番茄小说增长实验当前最大的风险是什么？", "u_alice",
                        "ANSWERED", List.of("内容成本"), List.of("内容成本"), Set.of("p_tomato")),
                new EvalCase("risk-compare", "比较番茄小说和红果短剧当前最大的风险。", "u_alice",
                        "ANSWERED", List.of("内容成本", "版权"), List.of("内容成本", "版权"),
                        Set.of("p_tomato", "p_duanju")),
                new EvalCase("decision", "红果短剧为什么限制同类内容连续推荐？", "u_alice",
                        "ANSWERED", List.of("探索", "多样性"), List.of("探索", "多样性"), Set.of("p_duanju")),
                new EvalCase("launch-date", "番茄小说安卓新版上线时间是什么时候？", "u_bob",
                        "ANSWERED", List.of("12月8日"), List.of("12月8日"), Set.of("p_tomato")),
                new EvalCase("abstain", "番茄小说在欧洲市场的覆盖率是多少？", "u_alice",
                        "ABSTAINED", List.of(), List.of(), Set.of()),
                new EvalCase("forbidden", "头条热点专题当前最大的风险是什么？", "u_alice",
                        "FORBIDDEN", List.of(), List.of(), Set.of())
        );
    }

    private EvaluationCaseResult runCase(EvalCase testCase) {
        try {
            AskResponse response = harness.ask(testCase.userId(),
                    new AskRequest(testCase.question(), List.of(), "eval_" + testCase.id()));
            List<String> failures = new ArrayList<>();
            if (!testCase.expected().equals(response.status())) {
                failures.add("期望状态 " + testCase.expected() + "，实际 " + response.status());
            }
            String answer = response.answer().toLowerCase(Locale.ROOT);
            for (String keyword : testCase.keywords()) {
                if (!answer.contains(keyword.toLowerCase(Locale.ROOT))) failures.add("缺少关键词：" + keyword);
            }
            String evidenceText = response.evidence().stream().map(item -> item.snippet())
                    .reduce("", (left, right) -> left + "\n" + right).toLowerCase(Locale.ROOT);
            boolean evidenceMatched = testCase.evidenceKeywords().isEmpty()
                    || testCase.evidenceKeywords().stream().allMatch(keyword -> evidenceText.contains(keyword.toLowerCase(Locale.ROOT)));
            if (!evidenceMatched) failures.add("期望证据未完整召回");
            for (String projectId : testCase.requiredProjectIds()) {
                if (response.citations().stream().noneMatch(item -> item.projectId().equals(projectId))) {
                    failures.add("缺少项目引用：" + projectId);
                }
            }
            return new EvaluationCaseResult(testCase.id(), testCase.question(), testCase.userId(),
                    testCase.expected(), response.status(), failures.isEmpty(), evidenceMatched,
                    failures, response.runId());
        } catch (ForbiddenException ex) {
            boolean passed = "FORBIDDEN".equals(testCase.expected());
            return new EvaluationCaseResult(testCase.id(), testCase.question(), testCase.userId(),
                    testCase.expected(), "FORBIDDEN", passed, true,
                    passed ? List.of() : List.of(ex.getMessage()), null);
        }
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 1.0 : Math.round((double) numerator / denominator * 10000.0) / 10000.0;
    }

    private record EvalCase(String id, String question, String userId, String expected,
                            List<String> keywords, List<String> evidenceKeywords,
                            Set<String> requiredProjectIds) {}
}
