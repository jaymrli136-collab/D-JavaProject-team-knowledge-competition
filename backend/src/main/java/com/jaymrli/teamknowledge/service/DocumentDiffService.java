package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.DocumentDiffView;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DocumentDiffService {
    private final KnowledgeStore store;
    private final AccessControlService accessControl;

    public DocumentDiffService(KnowledgeStore store, AccessControlService accessControl) {
        this.store = store;
        this.accessControl = accessControl;
    }

    public DocumentDiffView diff(String userId, String leftDocumentId, String rightDocumentId) {
        KnowledgeDocument left = store.findDocument(leftDocumentId)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + leftDocumentId));
        KnowledgeDocument right = store.findDocument(rightDocumentId)
                .orElseThrow(() -> new NotFoundException("文档不存在: " + rightDocumentId));
        if (!left.projectId().equals(right.projectId())) {
            throw new IllegalArgumentException("只能比较同一项目内的文档版本");
        }
        accessControl.requireAccessibleProject(userId, left.projectId());
        Set<String> leftLines = lines(left);
        Set<String> rightLines = lines(right);
        List<String> added = rightLines.stream().filter(line -> !leftLines.contains(line)).toList();
        List<String> removed = leftLines.stream().filter(line -> !rightLines.contains(line)).toList();
        return new DocumentDiffView(left.id(), left.version(), right.id(), right.version(), added, removed);
    }

    private Set<String> lines(KnowledgeDocument document) {
        Set<String> lines = new LinkedHashSet<>();
        document.sections().forEach(section -> {
            lines.add("[" + section.headingPath() + "]");
            for (String line : section.content().split("\\R")) {
                String value = line.trim();
                if (!value.isBlank()) lines.add(value);
            }
        });
        return lines;
    }
}
