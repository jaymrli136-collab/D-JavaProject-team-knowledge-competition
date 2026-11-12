package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.domain.Domain.Evidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EvidenceConsistencyChecker {
    private static final Pattern DATE = Pattern.compile("(\\d{1,2}月\\d{1,2}日)");

    public String annotate(List<Evidence> evidence, String answer) {
        if (evidence.isEmpty()) return answer;
        Map<String, List<Evidence>> byProject = new LinkedHashMap<>();
        for (Evidence item : evidence) byProject.computeIfAbsent(item.projectName(), ignored -> new ArrayList<>()).add(item);

        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, List<Evidence>> entry : byProject.entrySet()) {
            Set<String> dates = new LinkedHashSet<>();
            for (Evidence item : entry.getValue()) {
                Matcher matcher = DATE.matcher(item.snippet());
                while (matcher.find()) dates.add(matcher.group(1));
            }
            if (dates.size() > 1) {
                warnings.add(entry.getKey() + "存在多个日期表述：" + dates
                        + "。系统按文档生效日期排序，请优先核对较新的版本。");
            }
        }
        if (warnings.isEmpty()) return answer;
        return "时效提示：" + String.join("；", warnings) + "\n\n" + answer;
    }
}
