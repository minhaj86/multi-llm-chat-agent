package com.example.aichat.rag.infrastructure.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.DocumentExtractor;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.ExtractedDocument.SectionSpan;
import com.example.aichat.support.KnowledgeFixtures;
import com.example.aichat.support.RagTestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class DocumentExtractorsTest {

    private final RagProperties properties = RagTestProperties.defaults();

    private static ExtractedDocument extract(DocumentExtractor extractor, String filename, String contentType,
                                             byte[] content) throws IOException {
        assertThat(extractor.supports(contentType, filename)).isTrue();
        return extractor.extract(new ByteArrayInputStream(content), new DocumentMetadata(UUID.randomUUID(),
                UUID.randomUUID(), filename, contentType, content.length));
    }

    @Test
    void pdfTextKeepsPageBoundaries() throws IOException {
        byte[] pdf = KnowledgeFixtures.pdf(false, "Introduction to the pricing platform", "Currency conversion uses daily rates");

        ExtractedDocument document = extract(new PdfDocumentExtractor(properties), "pricing.pdf",
                "application/pdf", pdf);

        assertThat(document.text()).contains("Introduction to the pricing platform")
                .contains("Currency conversion uses daily rates");
        assertThat(document.pages()).hasSize(2);
        int conversion = document.text().indexOf("Currency conversion");
        assertThat(document.pageAt(0)).isEqualTo(1);
        assertThat(document.pageAt(conversion)).isEqualTo(2);
        assertThat(document.properties()).containsEntry("pages", "2");
    }

    @Test
    void encryptedPdfsFailWithAClearReason() throws IOException {
        byte[] pdf = KnowledgeFixtures.pdf(true, "Secret pricing");

        assertThatThrownBy(() -> extract(new PdfDocumentExtractor(properties), "secret.pdf", "application/pdf", pdf))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("Unsupported encryption");
    }

    @Test
    void docxHeadingsBecomeSections() throws IOException {
        byte[] docx = KnowledgeFixtures.docx();

        ExtractedDocument document = extract(new DocxDocumentExtractor(properties), "guide.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        assertThat(document.text()).contains("Architecture").contains("The pricing service converts currencies.");
        assertThat(document.sections()).extracting(SectionSpan::title).contains("Architecture");
        int body = document.text().indexOf("The pricing service");
        assertThat(document.sectionAt(body)).isEqualTo("Architecture");
    }

    @Test
    void htmlKeepsVisibleTextOnly() throws IOException {
        String html = "<html><head><title>Page</title><script>alert('x')</script><style>p{color:red}</style></head>"
                + "<body><h1>Pricing</h1><p>Currency rules apply.</p><script>evil()</script>"
                + "<table><tr><td>EUR</td><td>1.0</td></tr></table></body></html>";

        ExtractedDocument document = extract(new HtmlDocumentExtractor(properties), "page.html", "text/html",
                html.getBytes(StandardCharsets.UTF_8));

        assertThat(document.text()).contains("Pricing").contains("Currency rules apply.").contains("EUR | 1.0")
                .doesNotContain("alert").doesNotContain("evil").doesNotContain("color:red");
        assertThat(document.sections()).extracting(SectionSpan::title).containsExactly("Pricing");
    }

    @Test
    void markdownHeadingsOutsideCodeBlocksBecomeSections() throws IOException {
        String markdown = "# Guide\r\n\r\nIntro text.\n\n\n\n## Setup\n\n```\n# not a heading\n```\nDone.  \n";

        ExtractedDocument document = extract(new MarkdownDocumentExtractor(properties), "guide.md", "text/markdown",
                markdown.getBytes(StandardCharsets.UTF_8));

        assertThat(document.sections()).extracting(SectionSpan::title).containsExactly("Guide", "Setup");
        assertThat(document.text()).doesNotContain("\r").doesNotContain("\n\n\n").contains("# not a heading");
        assertThat(document.sectionAt(document.text().indexOf("Done."))).isEqualTo("Setup");
    }

    @Test
    void textFilesFallBackToWindows1252() throws IOException {
        byte[] legacy = "Caf\u00e9 pricing\u2014notes".getBytes(Charset.forName("windows-1252"));

        ExtractedDocument document = extract(new TextDocumentExtractor(properties), "notes.txt", "text/plain", legacy);

        assertThat(document.text()).isEqualTo("Caf\u00e9 pricing\u2014notes");
    }

    @Test
    void csvRowsBecomeColumnValuePairs() throws IOException {
        String csv = "currency;rate;comment\nEUR;1.0;\"base; reference\"\nSEK;11.5;\n";

        ExtractedDocument document = extract(new CsvDocumentExtractor(properties), "rates.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        assertThat(document.text()).isEqualTo("currency: EUR | rate: 1.0 | comment: base; reference\n"
                + "currency: SEK | rate: 11.5");
        assertThat(document.properties()).containsEntry("rows", "2");
    }

    @Test
    void jsonIsFlattenedWithTopLevelSections() throws IOException {
        String json = "{\"pricing\":{\"currency\":\"EUR\",\"markets\":[{\"code\":\"SE\"}]},\"version\":2}";

        ExtractedDocument document = extract(new JsonDocumentExtractor(properties, new ObjectMapper()), "config.json",
                "application/json", json.getBytes(StandardCharsets.UTF_8));

        assertThat(document.text()).isEqualTo("pricing.currency: EUR\npricing.markets[0].code: SE\nversion: 2");
        assertThat(document.sections()).extracting(SectionSpan::title).containsExactly("pricing", "version");
    }

    @Test
    void enforcesTheExtractedTextLimit() {
        RagProperties tiny = RagTestProperties.withIngestion(50, DataSize.ofMegabytes(1));
        byte[] text = "word ".repeat(100).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extract(new TextDocumentExtractor(tiny), "big.txt", "text/plain", text))
                .isInstanceOf(DocumentExtractionException.class).hasMessageContaining("exceeds the limit");
        assertThatThrownBy(() -> extract(new HtmlDocumentExtractor(tiny), "big.html", "text/html",
                ("<html><body><p>" + "word ".repeat(100) + "</p></body></html>").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentExtractionException.class).hasMessageContaining("exceeds the limit");
    }
}
