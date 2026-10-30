package com.simxe.vireon.openapi;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;

/** 严格读取构建期目录；运行时不访问源码、文件系统路径或Git。 */
final class OpenApiMetadata {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private final byte[] review;
    private final JsonNode history;
    OpenApiMetadata(OpenApiProperties properties) {
        review = properties.getReview().isEnabled() ? read("api-review.json") : null;
        JsonNode reviewDocument = review == null ? null : parse(review, false);
        history = properties.getGitHistory().isEnabled() || properties.getReview().isEnabled() ? parse(read("api-git-history.json"), true) : null;
        if (reviewDocument != null && history != null) {
            for (String field : Set.of("service", "revision", "inputDigest")) {
                if (!reviewDocument.get(field).equals(history.get(field))) throw new IllegalStateException("Documentation metadata inputs differ");
            }
        }
    }
    byte[] reviewBytes() { return review == null ? null : review.clone(); }
    JsonNode history() { return history; }
    private static byte[] read(String file) {
        try {
            var locations = OpenApiMetadata.class.getClassLoader().getResources("META-INF/vireon/" + file);
            if (!locations.hasMoreElements()) throw new IllegalStateException("Required documentation catalog missing");
            locations.nextElement();
            if (locations.hasMoreElements()) throw new IllegalStateException("Multiple documentation catalogs found");
        } catch (IOException failure) { throw new IllegalStateException("Cannot enumerate documentation catalogs", failure); }
        try (var input = new ClassPathResource("META-INF/vireon/" + file).getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalStateException("Documentation metadata too large");
            return bytes;
        } catch (IOException failure) { throw new IllegalStateException("Required classpath documentation metadata is unavailable", failure); }
    }
    static JsonNode parse(byte[] bytes, boolean git) {
        if (bytes.length > MAX_BYTES) throw new IllegalStateException("Documentation metadata too large");
        try {
            var factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(16000).maxNumberLength(32).build()).build();
            var mapper = new ObjectMapper(factory);
            JsonNode root;
            try (var parser = factory.createParser(bytes)) {
                root = mapper.readTree(parser);
                if (parser.nextToken() != null) throw new IllegalStateException("Trailing metadata content");
            }
            fields(root, git ? Set.of("schemaVersion", "service", "revision", "inputDigest", "operations")
                    : Set.of("schemaVersion", "service", "revision", "inputDigest", "internalEndpoints", "tables", "relationships", "permissions", "runtimeNotes"));
            if (!root.path("schemaVersion").isInt() || root.path("schemaVersion").intValue() != 1) throw new IllegalStateException("Unsupported metadata version");
            text(root, "service", "[A-Za-z0-9._-]{1,100}"); text(root, "revision", "(?:[a-f0-9]{40,64}|unavailable)"); text(root, "inputDigest", "[a-f0-9]{64}");
            if (git) {
                Set<String> ids = new HashSet<>(), handlers = new HashSet<>();
                for (JsonNode entry : array(root, "operations")) {
                    fields(entry, Set.of("operationId", "handler", "state", "commit", "timestamp"));
                    text(entry, "operationId", "[A-Za-z0-9._-]{1,160}"); text(entry, "handler", "[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+");
                    if (!ids.add(entry.get("operationId").asText()) || !handlers.add(entry.get("handler").asText())) throw new IllegalStateException("Duplicate operation mapping");
                    text(entry, "state", "committed|dirty|unavailable");
                    String state = entry.get("state").asText();
                    if (state.equals("committed")) {
                        text(entry, "commit", "[a-f0-9]{40,64}"); Instant.parse(entry.path("timestamp").asText());
                    } else if (!entry.path("commit").isNull() || !entry.path("timestamp").isNull()) throw new IllegalStateException("Noncommitted history must not claim a timestamp");
                }
            } else {
                for (JsonNode endpoint : array(root, "internalEndpoints")) {
                    fields(endpoint, Set.of("operationId", "method", "path", "summary"));
                    text(endpoint, "operationId", "[A-Za-z0-9._-]{1,160}"); text(endpoint, "method", "GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS");
                    string(endpoint, "path"); string(endpoint, "summary");
                }
                Set<String> tableNames = new HashSet<>();
                java.util.Map<String, Set<String>> tableColumns = new java.util.HashMap<>();
                for (JsonNode table : array(root, "tables")) {
                    fields(table, Set.of("name", "comment", "columns", "indexes")); string(table, "name"); string(table, "comment");
                    if (!tableNames.add(table.get("name").asText())) throw new IllegalStateException("Duplicate table");
                    Set<String> columns = new HashSet<>();
                    tableColumns.put(table.get("name").asText(), columns);
                    for (JsonNode column : array(table, "columns")) {
                        fields(column, Set.of("name", "type", "nullable", "comment"));
                        string(column, "name"); string(column, "type"); string(column, "comment");
                        if (!column.get("nullable").isBoolean() || !columns.add(column.get("name").asText())) throw new IllegalStateException("Invalid table column");
                    }
                    Set<String> indexes = new HashSet<>();
                    for (JsonNode index : array(table, "indexes")) {
                        fields(index, index.has("predicate")?Set.of("name", "unique", "columns","predicate"):Set.of("name", "unique", "columns")); string(index, "name");
                        if(index.has("predicate")) {
                            string(index,"predicate");
                            var predicate=java.util.regex.Pattern.compile("([a-z_][a-z0-9_]*) = (?:-?[0-9]+|(?i:TRUE|FALSE)|'(?:[^'\\\\\\r\\n]|'')*+')").matcher(index.get("predicate").textValue());
                            if(!predicate.matches() || !columns.contains(predicate.group(1)))throw new IllegalStateException("Invalid index predicate");
                        }
                        if (!index.get("unique").isBoolean() || !indexes.add(index.get("name").asText())) throw new IllegalStateException("Invalid index");
                        for (JsonNode column : array(index, "columns")) {
                            if (!column.isTextual() || !column.asText().matches("[a-z_][a-z0-9_]*(?: (?:ASC|DESC))?")
                                    || !columns.contains(column.asText().split(" ")[0])) throw new IllegalStateException("Invalid index column");
                        }
                    }
                }
                for (JsonNode relationship : array(root, "relationships")) {
                    Set<String> keys = Set.of("fromTable", "fromColumn", "toTable", "toColumn", "description"); fields(relationship, keys);
                    for (String key : keys) string(relationship, key);
                    for (String side : Set.of("from", "to")) {
                        Set<String> columns = tableColumns.get(relationship.get(side + "Table").asText());
                        if (columns == null || !columns.contains(relationship.get(side + "Column").asText())) throw new IllegalStateException("Invalid relationship reference");
                    }
                }
                Set<String> codes = new HashSet<>();
                for (JsonNode permission : array(root, "permissions")) {
                    fields(permission, Set.of("code", "description")); string(permission, "description"); text(permission, "code", "[A-Za-z0-9:._-]{1,128}");
                    if (!codes.add(permission.get("code").asText())) throw new IllegalStateException("Duplicate permission");
                }
                for (JsonNode note : array(root, "runtimeNotes")) { fields(note, Set.of("title", "description")); string(note, "title"); string(note, "description"); }
            }
            return root;
        } catch (IOException | IllegalArgumentException failure) { throw new IllegalStateException("Invalid documentation metadata", failure); }
    }
    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || node.size() != expected.size()) throw new IllegalStateException("Invalid metadata object");
        Iterator<String> keys = node.fieldNames();
        while (keys.hasNext()) if (!expected.contains(keys.next())) throw new IllegalStateException("Unknown metadata field");
    }
    private static JsonNode array(JsonNode node, String name) {
        JsonNode result = node.get(name);
        if (result == null || !result.isArray() || result.size() > 10000) throw new IllegalStateException("Invalid metadata collection");
        return result;
    }
    private static void string(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().length() > 16000 || value.textValue().indexOf('\u0000') >= 0) throw new IllegalStateException("Invalid metadata text");
    }
    private static void text(JsonNode node, String name, String pattern) { string(node, name); if (!node.get(name).asText().matches(pattern)) throw new IllegalStateException("Invalid metadata identifier"); }
}
