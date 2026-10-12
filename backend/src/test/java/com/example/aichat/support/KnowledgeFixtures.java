package com.example.aichat.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Generates real documents for extraction and ingestion tests.
 */
public final class KnowledgeFixtures {

    private KnowledgeFixtures() {
    }

    /** PDF with the given text per page ({@code \\n} separates lines; optionally password protected). */
    public static byte[] pdf(boolean encrypted, String... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    content.setLeading(15);
                    content.newLineAtOffset(60, 740);
                    for (String line : text.split("\n")) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret",
                        new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Minimal Word document with a "heading 1" paragraph style, written as raw OOXML parts. */
    public static byte[] docx() throws IOException {
        String w = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        String rel = "http://schemas.openxmlformats.org/package/2006/relationships";
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-"
                + "officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-"
                + "officedocument.wordprocessingml.styles+xml\"/></Types>");
        parts.put("_rels/.rels", "<Relationships xmlns=\"" + rel + "\"><Relationship Id=\"rId1\" Type=\""
                + "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                + "Target=\"word/document.xml\"/></Relationships>");
        parts.put("word/_rels/document.xml.rels", "<Relationships xmlns=\"" + rel + "\"><Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" "
                + "Target=\"styles.xml\"/></Relationships>");
        parts.put("word/styles.xml", "<w:styles xmlns:w=\"" + w + "\"><w:style w:type=\"paragraph\" "
                + "w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/></w:style></w:styles>");
        parts.put("word/document.xml", "<w:document xmlns:w=\"" + w + "\"><w:body>"
                + "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Architecture</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>The pricing service converts currencies.</w:t></w:r></w:p>"
                + "</w:body></w:document>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
