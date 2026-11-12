package com.jaymrli.teamknowledge;

import com.jaymrli.teamknowledge.service.DocumentParserService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserServiceTest {
    private final DocumentParserService parser = new DocumentParserService();

    @Test
    void shouldParsePlainText() {
        String text = "# 文档\n项目风险说明。";
        assertThat(parser.extract("note.md", text.getBytes(StandardCharsets.UTF_8))).contains("项目风险说明");
    }

    @Test
    void shouldParseDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("DOCX 知识内容");
            document.write(output);
            assertThat(parser.extract("knowledge.docx", output.toByteArray())).contains("DOCX 知识内容");
        }
    }
}
