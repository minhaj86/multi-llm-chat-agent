package com.example.aichat.rag.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.rag.domain.EmbeddingProvider;
import com.example.aichat.support.AbstractIntegrationTest;
import com.example.aichat.support.FakeEmbeddingProvider;
import com.example.aichat.support.KnowledgeFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class KnowledgeBaseIntegrationTest extends AbstractIntegrationTest {

    private static final String PRICING = """
            # Pricing architecture

            The pricing service converts currencies with the daily exchange rate published by the treasury team.
            Every market configures its own currency and rounding rules.

            ## Discounts

            Campaign discounts are applied after currency conversion and before value added tax is calculated.
            Discounts never stack with member prices.

            ## Releases

            Price lists are released every Monday morning and cached by the storefront for one hour.
            """;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    /** The pricing document with a unique reference, so tests never trip duplicate detection of each other. */
    private static String pricing() {
        return PRICING + "\nDocument reference " + UUID.randomUUID() + ".\n";
    }

    private String admin;

    @BeforeEach
    void setUp() {
        admin = adminToken();
        fake().reset();
    }

    private FakeEmbeddingProvider fake() {
        return (FakeEmbeddingProvider) embeddingProvider;
    }

    @Test
    void textEntriesAreChunkedEmbeddedStoredAndSearchable() {
        UUID collection = createCollection("Pricing");
        UUID id = addText("Internal Pricing Architecture", pricing(), collection, List.of("Pricing", "architecture"));

        JsonNode status = awaitStatus(id, "INDEXED");
        int chunks = status.get("chunkCount").asInt();
        assertThat(chunks).isGreaterThan(1);

        JsonNode details = getJson("/api/admin/knowledge/" + id, admin);
        assertThat(details.at("/source/embeddingModel").asText()).isEqualTo("fake-embedding");
        assertThat(details.at("/source/tags").toString()).isEqualTo("[\"pricing\",\"architecture\"]");
        assertThat(details.get("vectorCount").asInt()).isEqualTo(chunks);
        assertThat(details.get("createdByName").asText()).isEqualTo("admin");
        assertThat(count("SELECT COUNT(*) FROM knowledge_vectors WHERE source_id = ?", id)).isEqualTo(chunks);

        JsonNode content = getJson("/api/admin/knowledge/" + id + "/content", admin);
        assertThat(content.get("text").asText()).contains("daily exchange rate");
        assertThat(content.get("estimatedTokens").asInt()).isPositive();
        assertThat(content.get("sections").toString()).contains("Pricing architecture").contains("Discounts");

        JsonNode page = getJson("/api/admin/knowledge/" + id + "/chunks?size=50", admin);
        assertThat(page.get("total").asInt()).isEqualTo(chunks);
        assertThat(page.at("/items/0/chunkIndex").asInt()).isZero();
        assertThat(page.at("/items/0/sectionTitle").asText()).isEqualTo("Pricing architecture");

        JsonNode results = search("How are currencies converted with the exchange rate?", collection);
        assertThat(results.at("/results/0/sourceId").asText()).isEqualTo(id.toString());
        assertThat(results.at("/results/0/similarity").asDouble()).isGreaterThan(0.15);
        assertThat(results.get("embeddingModel").asText()).isEqualTo("fake-embedding");
        assertThat(results.at("/results/0/content").asText()).contains("exchange rate");
    }

    @Test
    void uploadedPdfsKeepPageNumbersForCitations() throws Exception {
        UUID collection = createCollection("Manuals");
        byte[] pdf = KnowledgeFixtures.pdf(false, String.join("\n",
                        "Warehouse handbook introduction for new colleagues joining the logistics team.",
                        "The handbook describes safety rules, opening hours and the badge system.",
                        "Every colleague receives a short training on forklift traffic in the hall.",
                        "Questions about shifts are answered by the team lead of the current week.",
                        "Lockers are assigned on the first day and must be emptied on the last day.",
                        "Visitors always register at the front desk and wear a yellow vest."),
                String.join("\n",
                        "Returned parcels are scanned at the dock and restocked within two days.",
                        "Damaged returns are photographed and moved to the inspection shelf.",
                        "The returns desk prints new labels when the original label is missing.",
                        "Restocked items are counted again during the weekly inventory check.",
                        "Refund approvals arrive automatically once the scan has been recorded.",
                        "Unclaimed returns are donated after ninety days in storage."));

        ResponseEntity<String> response = upload("Warehouse Handbook.pdf", pdf, Map.of("collectionId",
                collection.toString()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID id = UUID.fromString(read(response.getBody()).get("sourceId").asText());
        awaitStatus(id, "INDEXED");

        JsonNode details = getJson("/api/admin/knowledge/" + id, admin);
        assertThat(details.at("/source/typeLabel").asText()).isEqualTo("PDF");
        assertThat(details.at("/source/originalFilename").asText()).isEqualTo("Warehouse Handbook.pdf");
        assertThat(details.at("/ingestionStats/extractor").asText()).isEqualTo("pdf");

        JsonNode results = search("How are returned parcels restocked?", collection);
        assertThat(results.at("/results/0/sourceId").asText()).isEqualTo(id.toString());
        assertThat(results.at("/results/0/pageNumber").asInt()).isEqualTo(2);
    }

    @Test
    void duplicateUploadsAreDetectedAndCanBecomeNewVersionsOrReplacements() {
        UUID collection = createCollection("Duplicates");
        byte[] markdown = ("# Travel policy\n\nTrains are preferred over flights below five hours. "
                + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        UUID first = UUID.fromString(read(upload("travel.md", markdown, Map.of("collectionId", collection.toString()))
                .getBody()).get("sourceId").asText());
        awaitStatus(first, "INDEXED");
        int embeddedBefore = fake().embeddedTexts();

        ResponseEntity<String> duplicate = upload("travel-copy.md", markdown, Map.of("collectionId",
                collection.toString()));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode problem = read(duplicate.getBody());
        assertThat(problem.get("code").asText()).isEqualTo("DUPLICATE_DOCUMENT");
        assertThat(problem.at("/existing/0/id").asText()).isEqualTo(first.toString());
        assertThat(fake().embeddedTexts()).isEqualTo(embeddedBefore);

        JsonNode version = read(upload("travel.md", markdown, Map.of("collectionId", collection.toString(),
                "duplicateStrategy", "NEW_VERSION")).getBody());
        UUID second = UUID.fromString(version.get("sourceId").asText());
        assertThat(version.get("version").asInt()).isEqualTo(2);
        assertThat(version.get("relatedSourceId").asText()).isEqualTo(first.toString());
        awaitStatus(second, "INDEXED");
        assertThat(getJson("/api/admin/knowledge/" + first + "/status", admin).get("enabled").asBoolean()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM knowledge_vectors WHERE source_id = ? AND source_enabled", first))
                .isZero();

        JsonNode replaced = read(upload("travel.md", markdown, Map.of("collectionId", collection.toString(),
                "duplicateStrategy", "REPLACE")).getBody());
        assertThat(replaced.get("sourceId").asText()).isEqualTo(second.toString());
        assertThat(replaced.get("replaced").asBoolean()).isTrue();
        JsonNode status = awaitStatus(second, "INDEXED");
        assertThat(count("SELECT COUNT(*) FROM knowledge_vectors WHERE source_id = ?", second))
                .isEqualTo(status.get("chunkCount").asInt());
        assertThat(getJson("/api/admin/knowledge/" + second, admin).at("/source/version").asInt()).isEqualTo(3);
    }

    @Test
    void replacingAFileKeepsTheMetadataThatIsNotSent() {
        UUID collection = createCollection("Replace");
        String marker = UUID.randomUUID().toString();
        UUID id = UUID.fromString(read(upload("handbook.md",
                ("# Handbook\n\nExpense reports are due monthly. " + marker).getBytes(StandardCharsets.UTF_8),
                Map.of("title", "Employee handbook", "collectionId", collection.toString(), "tags", "hr,policies",
                        "sourceUrl", "https://intranet.example.com/handbook"))
                .getBody()).get("sourceId").asText());
        String indexedAt = awaitStatus(id, "INDEXED").get("indexedAt").asText();

        JsonNode replaced = read(upload("handbook-2026.md",
                ("# Handbook\n\nExpense reports are due weekly. " + marker).getBytes(StandardCharsets.UTF_8),
                Map.of("replaceSourceId", id.toString())).getBody());
        assertThat(replaced.get("sourceId").asText()).isEqualTo(id.toString());
        indexedAt = awaitIndexedAfter(id, indexedAt).get("indexedAt").asText();
        JsonNode source = getJson("/api/admin/knowledge/" + id, admin).get("source");
        assertThat(source.get("title").asText()).isEqualTo("Employee handbook");
        assertThat(source.get("originalFilename").asText()).isEqualTo("handbook-2026.md");
        assertThat(source.get("collectionId").asText()).isEqualTo(collection.toString());
        assertThat(source.get("tags")).extracting(JsonNode::asText).containsExactly("hr", "policies");
        assertThat(source.get("sourceUrl").asText()).isEqualTo("https://intranet.example.com/handbook");
        assertThat(source.get("version").asInt()).isEqualTo(2);
        assertThat(getJson("/api/admin/knowledge/" + id + "/content", admin).get("text").asText())
                .contains("due weekly").doesNotContain("due monthly");
        assertThat(count("SELECT COUNT(*) FROM knowledge_vectors WHERE source_id = ? AND collection_id = ?", id,
                collection)).isEqualTo(source.get("chunkCount").asLong());

        upload("handbook-2027.md", ("# Handbook\n\nExpense reports are due daily. " + marker)
                .getBytes(StandardCharsets.UTF_8), Map.of("replaceSourceId", id.toString(), "tags", "", "sourceUrl", ""));
        awaitIndexedAfter(id, indexedAt);
        source = getJson("/api/admin/knowledge/" + id, admin).get("source");
        assertThat(source.get("tags")).isEmpty();
        assertThat(source.get("sourceUrl").isNull()).isTrue();
        assertThat(source.get("title").asText()).isEqualTo("Employee handbook");
        assertThat(source.get("version").asInt()).isEqualTo(3);
    }

    @Test
    void deletingASourceRemovesItsChunksEmbeddingsAndFiles() {
        UUID collection = createCollection("Deletion");
        UUID id = addText("Temporary note", pricing(), collection, List.of());
        int chunks = awaitStatus(id, "INDEXED").get("chunkCount").asInt();

        ResponseEntity<String> response = call(HttpMethod.DELETE, "/api/admin/knowledge/" + id, null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = read(response.getBody());
        assertThat(body.get("deletedChunks").asInt()).isEqualTo(chunks);
        assertThat(body.get("deletedEmbeddings").asInt()).isEqualTo(chunks);
        for (String table : List.of("knowledge_vectors", "knowledge_chunks", "knowledge_source_contents",
                "knowledge_source_files")) {
            assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE source_id = ?", id)).as(table).isZero();
        }
        assertThat(count("SELECT COUNT(*) FROM knowledge_sources WHERE id = ?", id)).isZero();
        assertThat(call(HttpMethod.GET, "/api/admin/knowledge/" + id, null, admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(search("currency exchange rate", collection).get("results")).isEmpty();
    }

    @Test
    void disabledSourcesAndCollectionsAreExcludedFromRetrieval() {
        UUID collection = createCollection("Toggles");
        UUID id = addText("Pricing toggles", pricing(), collection, List.of());
        awaitStatus(id, "INDEXED");
        String question = "Which exchange rate converts currencies?";
        assertThat(search(question, collection).get("results")).isNotEmpty();

        call(HttpMethod.POST, "/api/admin/knowledge/" + id + "/disable", null, admin);
        assertThat(search(question, collection).get("results")).isEmpty();
        assertThat(getJson("/api/admin/knowledge/" + id + "/chunks", admin).get("total").asInt()).isPositive();

        call(HttpMethod.POST, "/api/admin/knowledge/" + id + "/enable", null, admin);
        assertThat(search(question, collection).get("results")).isNotEmpty();

        call(HttpMethod.PUT, "/api/admin/knowledge/collections/" + collection, Map.of("name", "Toggles-"
                + collection, "enabled", false), admin);
        assertThat(search(question, collection).get("results")).isEmpty();
    }

    @Test
    void reindexingReplacesVectorsWithoutDuplicates() {
        UUID collection = createCollection("Reindex");
        UUID id = addText("Reindexed note", pricing(), collection, List.of());
        JsonNode before = awaitStatus(id, "INDEXED");
        List<String> chunkIdsBefore = chunkIds(id);

        ResponseEntity<String> response = call(HttpMethod.POST, "/api/admin/knowledge/" + id + "/reindex", null,
                admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode after = awaitIndexedAfter(id, before.get("indexedAt").asText());

        assertThat(after.get("chunkCount").asInt()).isEqualTo(before.get("chunkCount").asInt());
        assertThat(count("SELECT COUNT(*) FROM knowledge_vectors WHERE source_id = ?", id))
                .isEqualTo(after.get("chunkCount").asInt());
        assertThat(chunkIds(id)).doesNotContainAnyElementsOf(chunkIdsBefore);
    }

    @Test
    void editingManualTextReplacesTheOldKnowledge() {
        UUID collection = createCollection("Editing");
        UUID id = addText("Office guide", "# Office\n\nThe cafeteria serves breakfast from seven o'clock.",
                collection, List.of());
        String indexedAt = awaitStatus(id, "INDEXED").get("indexedAt").asText();

        ResponseEntity<String> response = call(HttpMethod.PUT, "/api/admin/knowledge/" + id, Map.of(
                "title", "Office guide", "collectionId", collection.toString(), "tags", List.of("office"),
                "content", "# Office\n\nThe parking garage closes at midnight on weekdays."), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        awaitIndexedAfter(id, indexedAt);

        assertThat(search("When does the cafeteria serve breakfast?", collection).get("results")).isEmpty();
        assertThat(search("When does the parking garage close?", collection).at("/results/0/sourceId").asText())
                .isEqualTo(id.toString());
        JsonNode details = getJson("/api/admin/knowledge/" + id, admin);
        assertThat(details.at("/source/version").asInt()).isEqualTo(2);
        assertThat(details.get("vectorCount").asInt()).isEqualTo(details.at("/source/chunkCount").asInt());
    }

    @Test
    void failedIngestionReportsTheReasonAndCanBeRetried() {
        UUID collection = createCollection("Failures");
        fake().failing(true);
        UUID id = addText("Unlucky note", pricing(), collection, List.of());

        JsonNode failed = awaitStatus(id, "FAILED");
        assertThat(failed.get("errorMessage").asText()).contains("unavailable");
        assertThat(count("SELECT COUNT(*) FROM knowledge_vectors WHERE source_id = ?", id)).isZero();

        fake().failing(false);
        call(HttpMethod.POST, "/api/admin/knowledge/" + id + "/reindex", null, admin);
        assertThat(awaitStatus(id, "INDEXED").get("chunkCount").asInt()).isPositive();
    }

    @Test
    void deletingACollectionRemovesItsKnowledgeButTheDefaultCollectionStays() {
        UUID collection = createCollection("Short-lived");
        UUID id = addText("Short-lived note", pricing(), collection, List.of());
        awaitStatus(id, "INDEXED");

        JsonNode deleted = read(call(HttpMethod.DELETE, "/api/admin/knowledge/collections/" + collection, null, admin)
                .getBody());
        assertThat(deleted.get("deletedSources").asInt()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM knowledge_vectors WHERE collection_id = ?", collection)).isZero();
        assertThat(count("SELECT COUNT(*) FROM knowledge_sources WHERE id = ?", id)).isZero();

        String defaultId = null;
        for (JsonNode c : getJson("/api/admin/knowledge/collections", admin)) {
            if (c.get("defaultCollection").asBoolean()) {
                defaultId = c.get("id").asText();
            }
        }
        ResponseEntity<String> refused = call(HttpMethod.DELETE, "/api/admin/knowledge/collections/" + defaultId,
                null, admin);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(read(refused.getBody()).get("code").asText()).isEqualTo("DEFAULT_COLLECTION");
    }

    @Test
    void uploadsAreValidatedAndFileNamesSanitised() {
        UUID collection = createCollection("Validation");
        ResponseEntity<String> exe = upload("setup.exe", new byte[]{'M', 'Z', 1, 2}, Map.of());
        assertThat(exe.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(read(exe.getBody()).get("code").asText()).isEqualTo("UNSUPPORTED_FILE_TYPE");

        ResponseEntity<String> disguised = upload("report.pdf", "plain text".getBytes(StandardCharsets.UTF_8),
                Map.of());
        assertThat(read(disguised.getBody()).get("code").asText()).isEqualTo("CONTENT_MISMATCH");

        ResponseEntity<String> traversal = upload("../../etc/notes.md", ("# Notes\n\n" + UUID.randomUUID())
                .getBytes(StandardCharsets.UTF_8), Map.of("collectionId", collection.toString()));
        assertThat(traversal.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID id = UUID.fromString(read(traversal.getBody()).get("sourceId").asText());
        assertThat(getJson("/api/admin/knowledge/" + id, admin).at("/source/originalFilename").asText())
                .isEqualTo("notes.md");

        ResponseEntity<String> badUrl = call(HttpMethod.POST, "/api/admin/knowledge/text", Map.of("title", "Link",
                "content", "text", "sourceUrl", "javascript:alert(1)"), admin);
        assertThat(read(badUrl.getBody()).get("code").asText()).isEqualTo("INVALID_URL");
    }

    @Test
    void onlyAdministratorsManageTheKnowledgeBaseWhileUsersSeeEnabledCitations() {
        UUID collection = createCollection("Shared");
        UUID id = addText("Shared knowledge", pricing(), collection, List.of());
        awaitStatus(id, "INDEXED");
        String user = register("kb-user-" + UUID.randomUUID().toString().substring(0, 8));

        assertThat(call(HttpMethod.GET, "/api/admin/knowledge", null, user).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/api/admin/knowledge/text", Map.of("title", "x", "content", "y"), user)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/api/admin/knowledge/search", Map.of("query", "x"), user).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.DELETE, "/api/admin/knowledge/" + id, null, user).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode visible = getJson("/api/knowledge/collections", user);
        assertThat(visible.get("enabled").asBoolean()).isTrue();
        assertThat(visible.get("collections").toString()).contains(collection.toString());

        String chunkId = chunkIds(id).getFirst();
        JsonNode chunk = getJson("/api/knowledge/chunks/" + chunkId, user);
        assertThat(chunk.get("title").asText()).isEqualTo("Shared knowledge");
        assertThat(chunk.get("content").asText()).isNotBlank();

        call(HttpMethod.POST, "/api/admin/knowledge/" + id + "/disable", null, admin);
        assertThat(call(HttpMethod.GET, "/api/knowledge/chunks/" + chunkId, null, user).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theOverviewFiltersSortsAndPages() {
        UUID collection = createCollection("Overview");
        UUID b = addText("Beta handbook", "Beta content " + UUID.randomUUID(), collection, List.of("hr"));
        UUID a = addText("Alpha handbook", "Alpha content " + UUID.randomUUID(), collection, List.of("hr"));
        UUID c = addText("Gamma notes", "Gamma content " + UUID.randomUUID(), collection, List.of("it"));
        awaitStatus(a, "INDEXED");
        awaitStatus(b, "INDEXED");
        awaitStatus(c, "INDEXED");

        JsonNode page = getJson("/api/admin/knowledge?collectionId=" + collection
                + "&tag=HR&sort=TITLE&direction=asc&size=1&page=0", admin);
        assertThat(page.get("total").asInt()).isEqualTo(2);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.at("/items/0/title").asText()).isEqualTo("Alpha handbook");
        assertThat(page.at("/items/0/collectionName").asText()).startsWith("Overview");

        JsonNode text = getJson("/api/admin/knowledge?collectionId=" + collection + "&q=gamma&type=TEXT"
                + "&status=INDEXED&enabled=true", admin);
        assertThat(text.get("total").asInt()).isEqualTo(1);

        JsonNode stats = getJson("/api/admin/knowledge/stats", admin);
        assertThat(stats.at("/totals/sources").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(stats.at("/embedding/model").asText()).isEqualTo("fake-embedding");
        assertThat(stats.at("/settings/supportedTypes").asText()).contains("PDF").contains("DOCX");
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private UUID createCollection(String prefix) {
        ResponseEntity<String> response = call(HttpMethod.POST, "/api/admin/knowledge/collections",
                Map.of("name", prefix + "-" + UUID.randomUUID().toString().substring(0, 8), "description",
                        "Test collection"), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(read(response.getBody()).get("id").asText());
    }

    private UUID addText(String title, String content, UUID collection, List<String> tags) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("collectionId", collection.toString());
        body.put("tags", tags);
        ResponseEntity<String> response = call(HttpMethod.POST, "/api/admin/knowledge/text", body, admin);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode json = read(response.getBody());
        assertThat(json.get("status").asText()).isEqualTo("UPLOADED");
        return UUID.fromString(json.get("sourceId").asText());
    }

    private ResponseEntity<String> upload(String filename, byte[] content, Map<String, String> params) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        params.forEach(body::add);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(admin);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        return rest.exchange("/api/admin/knowledge/files", HttpMethod.POST, new HttpEntity<>(body, headers),
                String.class);
    }

    private JsonNode search(String query, UUID collection) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("collectionIds", List.of(collection.toString()));
        ResponseEntity<String> response = call(HttpMethod.POST, "/api/admin/knowledge/search", body, admin);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return read(response.getBody());
    }

    private List<String> chunkIds(UUID sourceId) {
        List<String> ids = new ArrayList<>();
        getJson("/api/admin/knowledge/" + sourceId + "/chunks?size=200", admin).get("items")
                .forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private JsonNode awaitStatus(UUID id, String expected) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        JsonNode status;
        do {
            status = getJson("/api/admin/knowledge/" + id + "/status", admin);
            if (expected.equals(status.get("status").asText())) {
                return status;
            }
            sleep();
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Source " + id + " did not reach " + expected + ": " + status);
    }

    private JsonNode awaitIndexedAfter(UUID id, String previousIndexedAt) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        JsonNode status;
        do {
            status = getJson("/api/admin/knowledge/" + id + "/status", admin);
            if ("INDEXED".equals(status.get("status").asText())
                    && !previousIndexedAt.equals(status.get("indexedAt").asText())) {
                return status;
            }
            sleep();
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Source " + id + " was not reindexed: " + status);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
