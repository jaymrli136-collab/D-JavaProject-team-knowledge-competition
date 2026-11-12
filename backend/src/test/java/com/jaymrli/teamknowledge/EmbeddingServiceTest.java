package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import com.jaymrli.teamknowledge.service.EmbeddingService;
import com.jaymrli.teamknowledge.service.TextAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingServiceTest {
    @Test
    void shouldFallbackToDeterministicHashEmbedding() {
        TextAnalyzer analyzer = new TextAnalyzer();
        EmbeddingService service = new EmbeddingService(analyzer,
                new TeamKnowledgeProperties(
                        new TeamKnowledgeProperties.Llm("mock", "", "", "", 0.1, "text-embedding-3-small"),
                        new TeamKnowledgeProperties.Retrieval(6, 15000)),
                RestClient.builder());
        double[] first = service.embed("多项目知识检索");
        double[] second = service.embed("多项目知识检索");
        assertThat(first).hasSize(TextAnalyzer.VECTOR_DIMENSION);
        assertThat(first).containsExactly(second);
        assertThat(first).isSameAs(second);
        assertThat(service.embedBatch(java.util.List.of("多项目知识检索", "另一个片段")))
                .hasSize(2);
    }
}
