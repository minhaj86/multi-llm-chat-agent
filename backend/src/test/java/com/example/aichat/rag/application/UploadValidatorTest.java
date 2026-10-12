package com.example.aichat.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.application.common.ValidationException;
import com.example.aichat.rag.infrastructure.extractor.TikaContentTypeDetector;
import com.example.aichat.support.RagTestProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class UploadValidatorTest {

    private final UploadValidator validator = new UploadValidator(new TikaContentTypeDetector(),
            RagTestProperties.withIngestion(5_000_000, DataSize.ofKilobytes(64)));

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void stripsPathsAndDangerousCharactersFromFileNames() {
        assertThat(UploadValidator.sanitizeFilename("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(UploadValidator.sanitizeFilename("C:\\Users\\admin\\Guide.md")).isEqualTo("Guide.md");
        assertThat(UploadValidator.sanitizeFilename("re\u0000port<script>.pdf")).isEqualTo("reportscript.pdf");
        assertThat(UploadValidator.sanitizeFilename("  ..hidden notes.txt ")).isEqualTo("hidden notes.txt");
        assertThat(UploadValidator.sanitizeFilename("a".repeat(400) + ".docx")).hasSize(255).endsWith(".docx");
        assertThatThrownBy(() -> UploadValidator.sanitizeFilename("../"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void acceptsSupportedFilesAndComputesTheChecksum() throws IOException {
        UploadValidator.UploadedFile markdown = validator.validate("guide.md", text("# Guide\n\nHello"));
        assertThat(markdown.type()).isEqualTo(DocumentType.MARKDOWN);
        assertThat(markdown.checksum()).hasSize(64);

        assertThat(validator.validate("data.json", text("{\"a\": 1}")).type()).isEqualTo(DocumentType.JSON);
        assertThat(validator.validate("rows.csv", text("a,b\n1,2\n")).type()).isEqualTo(DocumentType.CSV);
        assertThat(validator.validate("page.html", text("<html><body><p>x</p></body></html>")).type())
                .isEqualTo(DocumentType.HTML);
        assertThat(validator.validate("doc.pdf", emptyPdf()).type()).isEqualTo(DocumentType.PDF);
    }

    @Test
    void rejectsUnsupportedEmptyOversizedAndDisguisedFiles() throws IOException {
        assertThatThrownBy(() -> validator.validate("tool.exe", text("MZ")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Unsupported file type");
        assertThatThrownBy(() -> validator.validate("empty.txt", new byte[0]))
                .isInstanceOf(ValidationException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> validator.validate("big.txt", new byte[70 * 1024]))
                .isInstanceOf(ValidationException.class).hasMessageContaining("larger than the limit");
        assertThatThrownBy(() -> validator.validate("fake.pdf", text("just some text pretending to be a PDF")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("does not match");
        assertThatThrownBy(() -> validator.validate("notes.txt", emptyPdf()))
                .isInstanceOf(ValidationException.class);
        byte[] binary = {0x00, 0x01, 0x02, 0x03, 'a', 'b'};
        assertThatThrownBy(() -> validator.validate("binary.txt", binary))
                .isInstanceOf(ValidationException.class);
    }

    private static byte[] emptyPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}
