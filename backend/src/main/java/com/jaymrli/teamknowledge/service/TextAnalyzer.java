package com.jaymrli.teamknowledge.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TextAnalyzer {
    public static final int VECTOR_DIMENSION = 384;
    private static final int SNIPPET_LENGTH = 360;

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        StringBuilder han = new StringBuilder();
        StringBuilder latin = new StringBuilder();
        text.toLowerCase(Locale.ROOT).codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushLatin(latin, tokens);
                han.appendCodePoint(codePoint);
            } else if (Character.isLetterOrDigit(codePoint)) {
                flushHan(han, tokens);
                latin.appendCodePoint(codePoint);
            } else {
                flushHan(han, tokens);
                flushLatin(latin, tokens);
            }
        });
        flushHan(han, tokens);
        flushLatin(latin, tokens);
        return tokens;
    }

    public Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String token : tokenize(text)) frequency.merge(token, 1, Integer::sum);
        return frequency;
    }

    public double[] vectorize(String text) {
        double[] vector = new double[VECTOR_DIMENSION];
        for (Map.Entry<String, Integer> entry : termFrequency(text).entrySet()) {
            int hash = entry.getKey().hashCode();
            int index = Math.floorMod(hash, VECTOR_DIMENSION);
            double sign = hash < 0 ? -1.0 : 1.0;
            vector[index] += sign * (1.0 + Math.log(entry.getValue()));
        }
        normalize(vector);
        return vector;
    }

    public double cosine(double[] left, double[] right) {
        double sum = 0.0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) sum += left[i] * right[i];
        return Math.max(0.0, sum);
    }

    public String snippet(String content) {
        String compact = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return compact.length() <= SNIPPET_LENGTH ? compact : compact.substring(0, SNIPPET_LENGTH) + "...";
    }

    public String plainText(String markdown) {
        if (markdown == null) return "";
        return markdown.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("[*_\\x60>\\[\\]()]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void flushHan(StringBuilder builder, List<String> tokens) {
        if (builder.isEmpty()) return;
        String value = builder.toString();
        for (int i = 0; i < value.length(); i++) tokens.add(String.valueOf(value.charAt(i)));
        for (int i = 0; i + 1 < value.length(); i++) tokens.add(value.substring(i, i + 2));
        builder.setLength(0);
    }

    private void flushLatin(StringBuilder builder, List<String> tokens) {
        if (!builder.isEmpty()) {
            tokens.add(builder.toString());
            builder.setLength(0);
        }
    }

    private void normalize(double[] vector) {
        double norm = 0.0;
        for (double value : vector) norm += value * value;
        if (norm == 0.0) return;
        double divisor = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) vector[i] = vector[i] / divisor;
    }
}
