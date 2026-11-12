package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.AskRequest;
import com.jaymrli.teamknowledge.api.ApiDtos.AskResponse;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationCaseResult;
import com.jaymrli.teamknowledge.api.ApiDtos.EvaluationReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EvaluationService {
    private final KnowledgeAgentHarness harness;

    public EvaluationService(KnowledgeAgentHarness harness) { this.harness = harness; }

    public EvaluationReport runAll() {
        List<EvalCase> cases = List.of(
                new EvalCase("fact-risk", "番茄小说增长实验当前最大的风险是什么？", "u_alice",
                        "ANSWERED", List.of("内容成本"), Set.of("p_tomato"), false),
                new EvalCase("risk-compare", "比较番茄小说和红果短剧当前最大的风险。", "u_alice",
                        "ANSWERED", List.of("内容成本", "版权"), Set.of("p_tomato", "p_duanju"), false),
                new EvalCase("decision", "红果短剧为什么限制同类内容连续推荐？", "u_alice",
                        "ANSWERED", List.of("探索", "多样性"), Set.of("p_duanju"), false),
                new EvalCase("launch-date", "番茄小说安卓新版上线时间是什么时候？", "u_bob",
                        "ANSWERED", List.of("12月8日"), Set.of("p_tomato"), false),
                new EvalCase("abstain", "番茄小说在欧洲市场的覆盖率是多少？", "u_alice",
                        "ABSTAINED", List.of(), Set.of(), false),
                new EvalCase("forbidden", "头条热点专题当前最大的风险是什么？", "u_alice",
                        "FORBIDDEN", List.of(), Set.of(), true)
        );

        List<EvaluationCaseResult> results = new ArrayList<>();
        for (EvalCase testCase : cases) results.add(runCase(testCase));
        int passed = (int) results.stream().filter(EvaluationCaseResult::passed).count();
        long answered = results.stream().filter(item -> "ANSWERED".equals(item.actualStatus())).count();
        long cited = results.stream().filter(item -> "ANSWERED".equals(item.actualStatus()) && item.passed()).count();
        long expectedAbstain = cases.stream().filter(item -> "ABSTAINED".equals(item.expected())).count();
        long correctAbstain = results.stream().filter(item -> "ABSTAINED".equals(item.expected())
                && "ABSTAINED".equals(item.actualStatus())).count();
        int permissionViolations = (int) results.stream().filter(item -> "FORBIDDEN".equals(item.expected())
                && !"FORBIDDEN".equals(item.actualStatus())).count();
        return new EvaluationReport(results.size(), passed, ratio(passed, results.size()),
                ratio(cited, answered), ratio(correctAbstain, expectedAbstain), permissionViolations, results);
    }

    private EvaluationCaseResult runCase(EvalCase testCase) {
        try {
            AskResponse response = harness.ask(testCase.userId(),
                    new AskRequest(testCase.question(), List.of(), "eval_" + testCase.id()));
            List<String> failures = new ArrayList<>();
            if (!testCase.expected().equals(response.status())) failures.add("期望状态 " + testCase.expected() + "，实际 " + response.status());
            String answer = response.answer().toLowerCase(Locale.ROOT);
            for (String keyword : testCase.keywords()) {
                if (!answer.contains(keyword.toLowerCase(Locale.ROOT))) failures.add("缺少关键词：" + keyword);
            }
            for (String projectId : testCase.requiredProjectIds()) {
                if (response.citations().stream().noneMatch(item -> item.projectId().equals(projectId))) {
                    failures.add("缺少项目引用：" + projectId);
                }
            }
            return new EvaluationCaseResult(testCase.id(), testCase.question(), testCase.userId(),
                    testCase.expected(), response.status(), failures.isEmpty(), failures, response.runId());
        } catch (ForbiddenException ex) {
            boolean passed = "FORBIDDEN".equals(testCase.expected());
            return new EvaluationCaseResult(testCase.id(), testCase.question(), testCase.userId(),
                    testCase.expected(), "FORBIDDEN", passed, passed ? List.of() : List.of(ex.getMessage()), null);
        }
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 1.0 : Math.round((double) numerator / denominator * 10000.0) / 10000.0;
    }

    private record EvalCase(String id, String question, String userId, String expected,
                            List<String> keywords, Set<String> requiredProjectIds, boolean permissionCase) {}
}
