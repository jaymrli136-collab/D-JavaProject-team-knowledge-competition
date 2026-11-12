package com.jaymrli.teamknowledge.service;

import com.jaymrli.teamknowledge.api.ApiDtos.IngestDocumentRequest;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeDocument;
import com.jaymrli.teamknowledge.domain.Domain.KnowledgeSection;
import com.jaymrli.teamknowledge.repository.KnowledgeStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentIngestionService {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private final KnowledgeStore store;
    private final AccessControlService accessControl;
    private final TextAnalyzer analyzer;

    public DocumentIngestionService(KnowledgeStore store, AccessControlService accessControl, TextAnalyzer analyzer) {
        this.store = store;
        this.accessControl = accessControl;
        this.analyzer = analyzer;
    }

    public KnowledgeDocument ingest(String userId, String projectId, IngestDocumentRequest request) {
        accessControl.requireAccessibleProject(userId, projectId);
        List<KnowledgeSection> sections = parseSections(request.content());
        String plain = analyzer.plainText(request.content());
        String summary = plain.length() > 160 ? plain.substring(0, 160) + "..." : plain;
        String headings = sections.stream().map(KnowledgeSection::headingPath).distinct().limit(8)
                .reduce((left, right) -> left + " / " + right).orElse("文档概览");
        KnowledgeDocument document = new KnowledgeDocument(
                "doc_" + UUID.randomUUID(), projectId, request.title().trim(), "markdown",
                request.sourceUri().trim(), request.version() == null ? 1 : Math.max(1, request.version()),
                request.effectiveDate(), request.tags() == null ? Set.of() : Set.copyOf(request.tags()),
                summary, "文档：" + request.title().trim() + "；章节目录：" + headings,
                sections, Instant.now()
        );
        store.addDocument(document);
        return document;
    }

    private List<KnowledgeSection> parseSections(String markdown) {
        List<KnowledgeSection> sections = new ArrayList<>();
        String currentHeading = "文档概览";
        StringBuilder current = new StringBuilder();
        int order = 1;
        for (String line : markdown.split("\\R")) {
            Matcher matcher = HEADING.matcher(line.trim());
            if (matcher.matches()) {
                addSection(sections, currentHeading, current, order++);
                currentHeading = matcher.group(2).trim();
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        addSection(sections, currentHeading, current, order);
        if (sections.isEmpty()) sections.add(new KnowledgeSection("sec_" + UUID.randomUUID(), "文档概览", markdown, 1));
        return sections;
    }

    private void addSection(List<KnowledgeSection> sections, String heading, StringBuilder content, int order) {
        String value = content.toString().trim();
        if (!value.isBlank()) sections.add(new KnowledgeSection("sec_" + UUID.randomUUID(), heading, value, order));
    }
}
