package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.service.TextAnalyzer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalyzerTest {
    private final TextAnalyzer analyzer = new TextAnalyzer();

    @Test
    void shouldTokenizeChineseAndEnglish() {
        assertThat(analyzer.tokenize("番茄小说 RAG 风险")).isNotEmpty();
        assertThat(analyzer.tokenize("番茄小说 RAG 风险")).contains("小说", "rag", "风险");
        assertThat(analyzer.vectorize("项目风险")).hasSize(TextAnalyzer.VECTOR_DIMENSION);
    }

    @Test
    void shouldRemoveMarkdownSymbolsForSummary() {
        assertThat(analyzer.plainText("# 标题\n**正文** code")).contains("标题").contains("正文").doesNotContain("#");
    }
}
