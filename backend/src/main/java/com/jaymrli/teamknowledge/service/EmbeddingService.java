package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.config.TeamKnowledgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int REMOTE_BATCH_SIZE = 64;

    private final TextAnalyzer analyzer;
    private final TeamKnowledgeProperties properties;
    private final RestClient client;
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    public EmbeddingService(TextAnalyzer analyzer, TeamKnowledgeProperties properties, RestClient.Builder builder) {
        this.analyzer = analyzer;
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.client = builder.requestFactory(factory).baseUrl(properties.llm().baseUrl()).build();
    }

    public double[] embed(String text) {
        String key = cacheKey(text);
        return cache.computeIfAbsent(key, ignored -> remoteOrHash(text));
    }

    public List<double[]> embedBatch(List<String> texts) {
        Map<String, double[]> resolved = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (String text : texts) {
            double[] cached = cache.get(cacheKey(text));
            if (cached != null) resolved.put(text, cached);
            else if (!missing.contains(text)) missing.add(text);
        }
        if (!missing.isEmpty() && remoteEnabled()) {
            for (int start = 0; start < missing.size(); start += REMOTE_BATCH_SIZE) {
                List<String> batch = missing.subList(start, Math.min(missing.size(), start + REMOTE_BATCH_SIZE));
                try {
                    Map<String, double[]> remote = remoteBatch(batch);
                    batch.forEach(text -> {
                        double[] vector = remote.get(text);
                        if (vector != null && vector.length > 0) {
                            normalize(vector);
                            cache.put(cacheKey(text), vector);
                            resolved.put(text, vector);
                        }
                    });
                } catch (RuntimeException ex) {
                    log.warn("OpenAI batch embedding failed, falling back to hash vectors: {}", ex.getMessage());
                }
            }
        }
        for (String text : missing) {
            if (!resolved.containsKey(text)) {
                double[] vector = analyzer.vectorize(text);
                cache.put(cacheKey(text), vector);
                resolved.put(text, vector);
            }
        }
        return texts.stream().map(resolved::get).toList();
    }

    private double[] remoteOrHash(String text) {
        if (remoteEnabled()) {
            try {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("model", properties.llm().embeddingModel());
                request.put("input", text);
                Map<String, Object> response = request(request);
                double[] vector = parseSingle(response);
                if (vector.length > 0) {
                    normalize(vector);
                    return vector;
                }
            } catch (RuntimeException ex) {
                log.warn("OpenAI embedding failed, falling back to hash vector: {}", ex.getMessage());
            }
        }
        return analyzer.vectorize(text);
    }

    @SuppressWarnings("unchecked")
    private Map<String, double[]> remoteBatch(List<String> texts) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.llm().embeddingModel());
        request.put("input", texts);
        Map<String, Object> response = request(request);
        Map<String, double[]> result = new HashMap<>();
        Object dataValue = response == null ? null : response.get("data");
        if (!(dataValue instanceof List<?> data)) return result;
        for (Object value : data) {
            if (!(value instanceof Map<?, ?> item)) continue;
            Object indexValue = item.get("index");
            if (!(indexValue instanceof Number index) || index.intValue() < 0 || index.intValue() >= texts.size()) continue;
            double[] vector = parseVector(item.get("embedding"));
            if (vector.length > 0) result.put(texts.get(index.intValue()), vector);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(Map<String, Object> request) {
        return client.post().uri("/embeddings")
                .header("Authorization", "Bearer " + properties.llm().apiKey())
                .body(request).retrieve().body(Map.class);
    }

    private double[] parseSingle(Map<String, Object> response) {
        if (response == null) return new double[0];
        Object dataValue = response.get("data");
        if (!(dataValue instanceof List<?> data) || data.isEmpty()) return new double[0];
        if (!(data.get(0) instanceof Map<?, ?> item)) return new double[0];
        return parseVector(item.get("embedding"));
    }

    private double[] parseVector(Object vectorValue) {
        if (!(vectorValue instanceof List<?> values)) return new double[0];
        double[] vector = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i) instanceof Number number ? number.doubleValue() : 0.0;
        }
        return vector;
    }

    private boolean remoteEnabled() {
        TeamKnowledgeProperties.Llm llm = properties.llm();
        return "openai".equalsIgnoreCase(llm.provider()) && llm.apiKey() != null && !llm.apiKey().isBlank();
    }

    private String cacheKey(String text) {
        return properties.llm().provider() + ":" + properties.llm().embeddingModel() + ":" + hash(text);
    }

    private String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode()) + ":" + text.length();
        }
    }

    private void normalize(double[] vector) {
        double norm = 0.0;
        for (double value : vector) norm += value * value;
        if (norm == 0.0) return;
        double divisor = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) vector[i] /= divisor;
    }
}
