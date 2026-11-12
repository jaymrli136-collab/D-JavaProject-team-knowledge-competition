package com.jaymrli.teamknowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaymrli.teamknowledge.domain.Domain.DocumentStatus;
import com.jaymrli.teamknowledge.domain.WorkspaceState;
import com.jaymrli.teamknowledge.repository.EvaluationHistoryRepository;
import com.jaymrli.teamknowledge.repository.FeedbackRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeGapRepository;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import com.jaymrli.teamknowledge.repository.ProjectMemoryRepository;
import com.jaymrli.teamknowledge.repository.ProjectRoleRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkspacePersistenceService {
    private static final Logger log = LoggerFactory.getLogger(WorkspacePersistenceService.class);

    private final KnowledgeStore knowledgeStore;
    private final ProjectMemoryRepository memoryRepository;
    private final ProjectRoleRepository roleRepository;
    private final KnowledgeGapRepository gapRepository;
    private final FeedbackRepository feedbackRepository;
    private final EvaluationHistoryRepository evaluationRepository;
    private final ObjectMapper objectMapper;
    private final Path stateFile;
    private final boolean enabled;

    public WorkspacePersistenceService(KnowledgeStore knowledgeStore, ProjectMemoryRepository memoryRepository,
                                       KnowledgeGapRepository gapRepository, FeedbackRepository feedbackRepository,
                                       EvaluationHistoryRepository evaluationRepository, ProjectRoleRepository roleRepository,
                                       ObjectMapper objectMapper,
                                       @Value("${app.persistence.state-file:../data/workspace-state.json}") String stateFile,
                                       @Value("${app.persistence.enabled:true}") boolean enabled) {
        this.knowledgeStore = knowledgeStore;
        this.memoryRepository = memoryRepository;
        this.roleRepository = roleRepository;
        this.gapRepository = gapRepository;
        this.feedbackRepository = feedbackRepository;
        this.evaluationRepository = evaluationRepository;
        this.objectMapper = objectMapper;
        this.stateFile = Path.of(stateFile).toAbsolutePath().normalize();
        this.enabled = enabled;
    }

    @PostConstruct
    public void restoreOnStartup() {
        if (!enabled || !Files.exists(stateFile)) return;
        try {
            WorkspaceState state = objectMapper.readValue(stateFile.toFile(), WorkspaceState.class);
            if (state.version() != 1) {
                log.warn("Ignore unsupported workspace state version {}", state.version());
                return;
            }
            knowledgeStore.restore(state.projects(), state.documents(), state.decisions(),
                    state.documentStatuses(), state.supersededBy());
            memoryRepository.replaceAll(state.memories());
            roleRepository.replaceAll(state.projectRoles());
            gapRepository.replaceAll(state.gaps());
            feedbackRepository.replaceAll(state.feedback());
            evaluationRepository.replaceAll(state.evaluations());
            log.info("Restored workspace state from {}", stateFile);
        } catch (Exception ex) {
            log.error("Failed to restore workspace state from {}", stateFile, ex);
        }
    }

    @Scheduled(fixedDelayString = "${app.persistence.save-interval-ms:5000}")
    public void savePeriodically() {
        save();
    }

    @PreDestroy
    public void saveOnShutdown() {
        save();
    }

    public synchronized void save() {
        if (!enabled) return;
        try {
            Map<String, String> statuses = new LinkedHashMap<>();
            Map<String, String> supersededBy = new LinkedHashMap<>();
            knowledgeStore.allDocuments().forEach(document -> {
                statuses.put(document.id(), knowledgeStore.status(document.id()).name());
                String superseded = knowledgeStore.supersededBy(document.id());
                if (superseded != null) supersededBy.put(document.id(), superseded);
            });
            WorkspaceState state = new WorkspaceState(1, Instant.now(), knowledgeStore.allProjects(),
                    knowledgeStore.allDocuments(), knowledgeStore.allProjects().stream()
                    .flatMap(project -> knowledgeStore.decisionsForProjects(java.util.Set.of(project.id())).stream())
                     .toList(), statuses, supersededBy, memoryRepository.all(), roleRepository.all(), gapRepository.all(),
                    feedbackRepository.all(), evaluationRepository.recent(Integer.MAX_VALUE));
            Files.createDirectories(stateFile.getParent());
            Path temporary = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
            try {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            log.error("Failed to persist workspace state to {}", stateFile, ex);
        }
    }
}
