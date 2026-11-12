package com.jaymrli.teamknowledge.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class UntrustedContentGuard {
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above)\\s+instructions?"),
            Pattern.compile("(?i)reveal\\s+(the\\s+)?system\\s+prompt"),
            Pattern.compile("(?i)(call|invoke|execute)\\s+(a\\s+)?tool"),
            Pattern.compile("忽略(之前|以上|前面).{0,16}(指令|提示|规则)"),
            Pattern.compile("(泄露|输出|展示).{0,12}(系统提示词|系统指令)"),
            Pattern.compile("(调用|执行).{0,8}(工具|命令|脚本)")
    );

    public String sanitizeForPrompt(String text) {
        if (text == null || text.isBlank()) return "";
        String sanitized = text;
        for (Pattern pattern : INJECTION_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[潜在注入指令已移除]");
        }
        return sanitized;
    }

    public boolean isSuspicious(String text) {
        if (text == null) return false;
        return INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(text).find());
    }
}
