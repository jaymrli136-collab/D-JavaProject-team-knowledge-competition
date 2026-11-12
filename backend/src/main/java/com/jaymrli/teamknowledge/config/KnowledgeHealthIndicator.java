package com.jaymrli.teamknowledge.config;

import com.jaymrli.teamknowledge.domain.Domain.DocumentStatus;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeGapStatus;
import com.jaymrli.teamknowledge.repository.KnowledgeGapRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeHealthIndicator implements HealthIndicator {
    private final KnowledgeStore store;
    private final KnowledgeGapRepository gapRepository;
    private final TeamKnowledgeProperties properties;

    public KnowledgeHealthIndicator(KnowledgeStore store, KnowledgeGapRepository gapRepository,
                                    TeamKnowledgeProperties properties) {
        this.store = store;
        this.gapRepository = gapRepository;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long activeDocuments = store.allDocuments().stream()
                .filter(document -> store.status(document.id()) == DocumentStatus.ACTIVE).count();
        long openGaps = gapRepository.all().stream()
                .filter(gap -> gap.status() == KnowledgeGapStatus.OPEN
                        || gap.status() == KnowledgeGapStatus.IN_REVIEW).count();
        Health.Builder builder = store.allProjects().isEmpty() || activeDocuments == 0
                ? Health.down() : Health.up();
        return builder
                .withDetail("projects", store.allProjects().size())
                .withDetail("activeDocuments", activeDocuments)
                .withDetail("openGaps", openGaps)
                .withDetail("llmProvider", properties.llm().provider())
                .withDetail("embeddingModel", properties.llm().embeddingModel())
                .build();
    }
}
