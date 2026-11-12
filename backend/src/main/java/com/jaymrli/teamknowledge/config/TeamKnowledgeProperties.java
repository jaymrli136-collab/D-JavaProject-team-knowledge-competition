package com.jaymrli.teamknowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record TeamKnowledgeProperties(Llm llm, Retrieval retrieval) {
    public record Llm(String provider, String baseUrl, String apiKey, String model, double temperature) {
    }

    public record Retrieval(int maxEvidence) {
    }
}
