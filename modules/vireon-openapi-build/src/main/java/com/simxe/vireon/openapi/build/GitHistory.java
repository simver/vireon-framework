package com.simxe.vireon.openapi.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class GitHistory {
    private final Path root;
    private final boolean complete;
    final String revision;

    GitHistory(Path source) {
        String top = command(source, "rev-parse", "--show-toplevel");
        root = top == null ? null : Path.of(top.strip());
        String head = root == null ? null : command(root, "rev-parse", "HEAD");
        revision = head != null && head.strip().matches("[0-9a-f]{40,64}") ? head.strip() : "unavailable";
        complete = root != null && "false".equals(trim(command(root, "rev-parse", "--is-shallow-repository")))
                && !revision.equals("unavailable");
    }

    void requireCleanInputs(List<Path> inputs, Path source, Path migrations) {
        if (!complete) throw new IllegalArgumentException("STRICT_HISTORY_REQUIRES_CLEAN_COMPLETE_HISTORY");
        // Directory status includes tracked deletions absent from the current filesystem enumeration.
        for (Path directory : List.of(source, migrations)) {
            Path normalized = directory.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) throw new IllegalArgumentException("STRICT_INPUT_OUTSIDE_REPOSITORY");
            String relative = root.relativize(normalized).toString().replace('\\', '/');
            String status = command(root, "status", "--porcelain=v1", "--untracked-files=all", "--", relative);
            if (status == null || !status.isBlank()) throw new IllegalArgumentException("STRICT_INPUT_DIRECTORY_NOT_CLEAN");
        }
        for (Path input : inputs) {
            Path normalized = input.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) throw new IllegalArgumentException("STRICT_INPUT_OUTSIDE_REPOSITORY");
            String relative = root.relativize(normalized).toString().replace('\\', '/');
            String tracked = command(root, "ls-files", "--error-unmatch", "--", relative);
            String status = command(root, "status", "--porcelain=v1", "--untracked-files=all", "--", relative);
            if (tracked == null || tracked.isBlank() || status == null || !status.isBlank())
                throw new IllegalArgumentException("STRICT_INPUT_NOT_CLEAN");
        }
    }

    Map<String, Object> operation(JavaEndpoints.Endpoint endpoint, boolean strict) {
        var result = new LinkedHashMap<String, Object>();
        result.put("operationId", endpoint.operationId()); result.put("handler", endpoint.handler());
        String state = "unavailable", commit = null, timestamp = null;
        if (root != null && endpoint.file().toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            String file = root.relativize(endpoint.file().toAbsolutePath().normalize()).toString().replace('\\', '/');
            String status = command(root, "status", "--porcelain=v1", "--untracked-files=all", "--", file);
            String diff = status == null ? null : command(root, "diff", "HEAD", "--no-ext-diff", "--no-textconv", "--unified=0", "--", file);
            boolean dirty = status != null && (status.startsWith("??") || status.startsWith("A ") || status.startsWith("R "));
            if (diff != null) {
                var hunks = Pattern.compile("(?m)^@@ -[0-9]+(?:,[0-9]+)? \\+([0-9]+)(?:,([0-9]+))? @@").matcher(diff);
                while (hunks.find()) {
                    int start = Integer.parseInt(hunks.group(1));
                    int count = hunks.group(2) == null ? 1 : Integer.parseInt(hunks.group(2));
                    // A deletion-only hunk identifies the line BEFORE the new range. Include that boundary.
                    int currentStart = count == 0 ? start + 1 : start;
                    if (currentStart <= endpoint.end() && currentStart + Math.max(count, 1) - 1 >= endpoint.start()) dirty = true;
                }
            }
            // Dirty wins even for shallow history; unrelated method edits do not taint this operation.
            if (dirty) state = "dirty";
            else if (status != null && diff != null && complete) {
                String rename = command(root, "log", "--follow", "--format=", "--name-status", "--", file);
                boolean renamed = rename == null || Pattern.compile("(?m)^R[0-9]+\\s").matcher(rename).find();
                if (!renamed) {
                    // -L is evaluated against HEAD. Translate current lines through unrelated edits.
                    int offset = 0;
                    var hunks = Pattern.compile("(?m)^@@ -[0-9]+(?:,([0-9]+))? \\+([0-9]+)(?:,([0-9]+))? @@").matcher(diff);
                    while (hunks.find()) {
                        int start = Integer.parseInt(hunks.group(2));
                        if (start < endpoint.start()) offset += (hunks.group(3) == null ? 1 : Integer.parseInt(hunks.group(3)))
                                - (hunks.group(1) == null ? 1 : Integer.parseInt(hunks.group(1)));
                    }
                    String history = command(root, "log", "--no-patch", "--format=%H %ct", "-L",
                            (endpoint.start() - offset) + "," + (endpoint.end() - offset) + ":" + file);
                    if (history != null && !history.isBlank()) {
                        String first = history.lines().filter(s -> !s.isBlank()).findFirst().orElse("");
                        if (first.matches("[0-9a-f]{40,64} [0-9]+")) {
                            String[] parts = first.split(" "); commit = parts[0];
                            timestamp = Instant.ofEpochSecond(Long.parseLong(parts[1])).toString(); state = "committed";
                        }
                    }
                }
            }
        }
        if (strict && !state.equals("committed")) throw new IllegalArgumentException("STRICT_HISTORY_REQUIRES_CLEAN_COMPLETE_HISTORY");
        result.put("state", state); result.put("commit", commit); result.put("timestamp", timestamp);
        return result;
    }

    private static String trim(String s) { return s == null ? "" : s.strip(); }
    private static String command(Path cwd, String... args) {
        var command = new ArrayList<>(List.of("git", "-c", "core.quotepath=false", "--no-pager"));
        command.addAll(List.of(args));
        Process process = null;
        try {
            process = new ProcessBuilder(command).directory(cwd.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            final Process running = process;
            var bytes = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return running.getInputStream().readNBytes(2 * 1024 * 1024 + 1); }
                catch (Exception e) { return new byte[0]; }
            });
            if (!process.waitFor(30, TimeUnit.SECONDS)) return null;
            byte[] output = bytes.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || output.length > 2 * 1024 * 1024) return null;
            return new String(output, StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
    }
}
