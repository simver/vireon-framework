package com.simxe.vireon.openapi.build;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Build-only catalog entry point. Requires JDK 25; no application or database execution. */
public final class ApiReviewGenerator {
    private static final int LIMIT = 2 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(16000).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private ApiReviewGenerator() {}

    public static void main(String[] args) throws Exception {
        var options = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length || !Set.of("--service", "--source", "--migrations", "--notes", "--output", "--strict-history", "--verify").contains(args[i])
                    || options.put(args[i], args[i + 1]) != null) throw new IllegalArgumentException("INVALID_ARGUMENTS");
        }
        for (var key : List.of("--service", "--source", "--migrations", "--notes", "--output"))
            if (!options.containsKey(key)) throw new IllegalArgumentException("MISSING_ARGUMENT:" + key);
        generate(options.get("--service"), Path.of(options.get("--source")), Path.of(options.get("--migrations")),
                Path.of(options.get("--notes")), Path.of(options.get("--output")), flag(options, "--strict-history"), flag(options, "--verify"));
    }

    public static void generate(String service, Path source, Path migrations, Path notes, Path output,
                                boolean strictHistory, boolean verify) throws Exception {
        if (!service.matches("[a-z][a-z0-9-]{0,79}")) throw new IllegalArgumentException("INVALID_SERVICE");
        source = source.toAbsolutePath().normalize(); migrations = migrations.toAbsolutePath().normalize();
        notes = notes.toAbsolutePath().normalize(); output = output.toAbsolutePath().normalize();
        if (output.startsWith(source) || output.startsWith(migrations)) throw new IllegalArgumentException("OUTPUT_OVERLAPS_INPUT");
        validateOutputPaths(output, notes);
        List<Path> java = files(source, ".java"), sql = files(migrations, ".sql");
        if (java.isEmpty()) throw new IllegalArgumentException("NO_JAVA_INPUT");
        byte[] noteBytes = bounded(notes);
        String digest = digest(source, migrations, java, sql, noteBytes);
        JsonNode note = JSON.readTree(noteBytes);
        fields(note, Set.of("relationships", "permissions", "runtimeNotes"));
        var tables = SqlModel.read(sql);
        validateNotes(note, tables);
        var endpoints = JavaEndpoints.read(java);
        if (endpoints.isEmpty()) throw new IllegalArgumentException("NO_ENDPOINTS");
        var git = new GitHistory(source);
        var inputs = new ArrayList<Path>(java); inputs.addAll(sql); inputs.add(notes);
        if (strictHistory) git.requireCleanInputs(inputs, source, migrations);
        var review = header(service, git.revision, digest);
        var hidden = new ArrayList<Map<String, Object>>();
        for (var endpoint : endpoints) if (endpoint.hidden()) {
            var item = new LinkedHashMap<String, Object>();
            item.put("operationId", endpoint.operationId()); item.put("method", endpoint.method());
            item.put("path", endpoint.path()); item.put("summary", endpoint.summary()); hidden.add(item);
        }
        review.put("internalEndpoints", hidden); review.put("tables", tables);
        review.put("relationships", note.get("relationships")); review.put("permissions", note.get("permissions"));
        review.put("runtimeNotes", note.get("runtimeNotes"));
        var history = header(service, git.revision, digest);
        var operations = new ArrayList<Map<String, Object>>();
        for (var endpoint : endpoints) operations.add(git.operation(endpoint, strictHistory));
        history.put("operations", operations);
        byte[] reviewJson = serialize(review), historyJson = serialize(history);
        // Prevent mixed input snapshots if a source changes while history is collected.
        if (!digest.equals(digest(source, migrations, files(source, ".java"), files(migrations, ".sql"), bounded(notes))))
            throw new IllegalArgumentException("INPUT_CHANGED_DURING_GENERATION");
        if (!git.revision.equals(new GitHistory(source).revision)) throw new IllegalArgumentException("REVISION_CHANGED_DURING_GENERATION");
        if (strictHistory) git.requireCleanInputs(inputs, source, migrations);
        validateOutputPaths(output, notes);
        Path directory = output.resolve("META-INF/vireon");
        if (verify) {
            if (!Arrays.equals(reviewJson, bounded(directory.resolve("api-review.json")))
                    || !Arrays.equals(historyJson, bounded(directory.resolve("api-git-history.json"))))
                throw new IllegalArgumentException("STALE_API_CATALOG");
        } else {
            Files.createDirectories(directory);
            Files.write(directory.resolve("api-review.json"), reviewJson);
            Files.write(directory.resolve("api-git-history.json"), historyJson);
        }
    }

    private static void validateOutputPaths(Path output, Path notes) throws Exception {
        for (Path target : List.of(output.resolve("META-INF/vireon/api-review.json"), output.resolve("META-INF/vireon/api-git-history.json"))) {
            if (target.equals(notes) || (Files.exists(target) && Files.isSameFile(target, notes)))
                throw new IllegalArgumentException("OUTPUT_OVERLAPS_NOTES");
            for (Path part = target; part != null; part = part.getParent()) {
                if (Files.exists(part, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(part)
                        || Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()
                        || !part.toRealPath().equals(part.toAbsolutePath().normalize())))
                    throw new IllegalArgumentException("OUTPUT_LINK_UNSUPPORTED");
            }
        }
    }

    private static boolean flag(Map<String, String> options, String name) {
        String value = options.getOrDefault(name, "false");
        if (!Set.of("true", "false").contains(value)) throw new IllegalArgumentException("INVALID_BOOLEAN");
        return value.equals("true");
    }
    private static LinkedHashMap<String, Object> header(String service, String revision, String digest) {
        var result = new LinkedHashMap<String, Object>(); result.put("schemaVersion", 1);
        result.put("service", service); result.put("revision", revision); result.put("inputDigest", digest); return result;
    }
    private static byte[] serialize(Object value) throws Exception {
        validateOutputText(JSON.valueToTree(value), 0);
        byte[] bytes = (JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LIMIT) throw new IllegalArgumentException("CATALOG_TOO_LARGE"); return bytes;
    }
    private static void validateOutputText(JsonNode node, int depth) {
        if (depth > 12) throw new IllegalArgumentException("CATALOG_TOO_DEEP");
        if (node.isTextual() && (node.textValue().length() > 16000 || node.textValue().indexOf('\0') >= 0))
            throw new IllegalArgumentException("INVALID_CATALOG_TEXT");
        if (node.isContainerNode()) for (var child : node) validateOutputText(child, depth + 1);
    }
    private static List<Path> files(Path root, String suffix) throws Exception {
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) throw new IllegalArgumentException("INPUT_DIRECTORY_REQUIRED");
        try (var paths = Files.walk(root)) {
            var all = paths.sorted().toList();
            if (all.stream().anyMatch(Files::isSymbolicLink)) throw new IllegalArgumentException("SYMLINK_INPUT_UNSUPPORTED");
            return all.stream().filter(p -> Files.isRegularFile(p) && p.toString().endsWith(suffix)).toList();
        }
    }
    private static byte[] bounded(Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path) || Files.size(path) > LIMIT)
            throw new IllegalArgumentException("MISSING_OR_OVERSIZED_INPUT");
        byte[] bytes = Files.readAllBytes(path); if (bytes.length > LIMIT) throw new IllegalArgumentException("OVERSIZED_INPUT"); return bytes;
    }
    private static String digest(Path source, Path migrations, List<Path> java, List<Path> sql, byte[] notes) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        for (var file : java) update(md, "java/" + source.relativize(file).toString().replace('\\', '/'), bounded(file));
        for (var file : sql) update(md, "sql/" + migrations.relativize(file).toString().replace('\\', '/'), bounded(file));
        update(md, "notes", notes); return HexFormat.of().formatHex(md.digest());
    }
    private static void update(MessageDigest md, String name, byte[] bytes) {
        md.update(name.getBytes(StandardCharsets.UTF_8)); md.update((byte) 0);
        md.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII)); md.update((byte) 0); md.update(bytes);
    }
    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("NOTES_OBJECT_REQUIRED");
        Set<String> found = new HashSet<>(); node.fieldNames().forEachRemaining(found::add);
        if (!found.equals(expected)) throw new IllegalArgumentException("INVALID_NOTES_FIELDS");
    }
    private static void validateNotes(JsonNode notes, List<Map<String, Object>> tables) {
        Set<String> columns = new HashSet<>();
        for (var table : tables) {
            @SuppressWarnings("unchecked") var cols = (List<Map<String, Object>>) table.get("columns");
            for (var column : cols) columns.add(table.get("name") + "." + column.get("name"));
        }
        for (var entry : Map.of("relationships", Set.of("fromTable", "fromColumn", "toTable", "toColumn", "description"),
                "permissions", Set.of("code", "description"), "runtimeNotes", Set.of("title", "description")).entrySet()) {
            var array = notes.get(entry.getKey());
            if (!array.isArray() || array.size() > 2000) throw new IllegalArgumentException("INVALID_NOTES_ARRAY");
            Set<String> unique = new HashSet<>();
            for (var item : array) {
                fields(item, entry.getValue());
                for (String key : entry.getValue()) if (!item.get(key).isTextual() || item.get(key).asText().isBlank()
                        || item.get(key).asText().length() > 16000 || item.get(key).asText().indexOf('\0') >= 0)
                    throw new IllegalArgumentException("INVALID_NOTE_TEXT");
                if (entry.getKey().equals("permissions") && !item.get("code").asText().matches("[A-Za-z0-9:._-]{1,128}"))
                    throw new IllegalArgumentException("INVALID_PERMISSION_CODE");
                String key = switch (entry.getKey()) {
                    case "permissions" -> item.get("code").asText();
                    case "runtimeNotes" -> item.get("title").asText();
                    default -> item.get("fromTable").asText() + "." + item.get("fromColumn").asText() + "->"
                            + item.get("toTable").asText() + "." + item.get("toColumn").asText();
                };
                if (!unique.add(key)) throw new IllegalArgumentException("DUPLICATE_NOTE");
                if (entry.getKey().equals("relationships") && (!columns.contains(item.get("fromTable").asText() + "." + item.get("fromColumn").asText())
                        || !columns.contains(item.get("toTable").asText() + "." + item.get("toColumn").asText())))
                    throw new IllegalArgumentException("UNKNOWN_RELATIONSHIP_COLUMN");
            }
        }
    }
}
