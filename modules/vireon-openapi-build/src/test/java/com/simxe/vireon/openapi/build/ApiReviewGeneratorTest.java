package com.simxe.vireon.openapi.build;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiReviewGeneratorTest {
    @Test void literalDefaultsAndPartialIndexPreserveModelWithoutLeakingDefaults() throws Exception {
        fixture();
        Files.writeString(temp.resolve("sql/V2__literals.sql"), """
            ALTER TABLE app.fact ADD COLUMN version bigint NOT NULL DEFAULT 0;
            ALTER TABLE app.fact ADD COLUMN enabled boolean DEFAULT false NOT NULL;
            ALTER TABLE app.fact ADD COLUMN flags jsonb DEFAULT '[]';
            ALTER TABLE app.fact ADD COLUMN label text DEFAULT 'NOT NULL UNIQUE; private''value';
            CREATE UNIQUE INDEX only_special ON app.fact (name) WHERE name='Special';
            """);
        run(false,false);
        var json=new ObjectMapper().readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-review.json")));
        var table=json.path("tables").get(0);
        assertEquals("name = 'Special'",table.path("indexes").get(3).path("predicate").asText());
        assertTrue(table.path("columns").get(5).path("nullable").asBoolean());
        assertFalse(json.toString().contains("private"));
        run(false,true);
    }
    @Test void unsupportedDefaultsPredicatesAndDuplicateModifiersRemainFatal() throws Exception {
        fixture();
        for(String sql:List.of(
            "ALTER TABLE app.fact ADD COLUMN x bigint DEFAULT 0 DEFAULT 1",
            "ALTER TABLE app.fact ADD COLUMN x bigint DEFAULT 0+1",
            "ALTER TABLE app.fact ADD COLUMN x text DEFAULT current_user",
            "ALTER TABLE app.fact ADD COLUMN x text DEFAULT 'x' NOT NULL NOT NULL",
            "CREATE INDEX test_partial ON app.fact (name) WHERE missing='x'",
            "CREATE INDEX test_partial ON app.fact (name) WHERE name='x' OR id=1",
            "CREATE INDEX test_partial ON app.fact (name) WHERE name=lower('x')")) {
            Files.writeString(temp.resolve("sql/V2__invalid.sql"),sql+";");
            assertThrows(IllegalArgumentException.class,()->run(false,false),sql);
        }
    }
    @TempDir Path temp;
    private static final String JAVA = """
            package demo;
            @RestController @RequestMapping("/api")
            class Example {
              @Hidden @PostMapping("/internal")
              @Operation(operationId="internal", summary="内部")
              String handle() { return "value"; }
              @GetMapping("/public") @Operation(operationId="publicRead")
              String read() { return "public"; }
            }
            """;
    private static final String SQL = """
            CREATE TABLE app.fact (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name varchar(64) NOT NULL UNIQUE);
            COMMENT ON TABLE app.fact IS '表;注释';
            COMMENT ON COLUMN app.fact.name IS '名称';
            CREATE INDEX ix_fact_name ON app.fact (name DESC, id);
            INSERT INTO app.fact(name) VALUES ('sensitive-never-output');
            """;
    void fixture() throws Exception {
        Files.createDirectories(temp.resolve("src")); Files.createDirectories(temp.resolve("sql"));
        Files.writeString(temp.resolve("src/Example.java"), JAVA);
        Files.writeString(temp.resolve("sql/V1__fact.sql"), SQL);
        Files.writeString(temp.resolve("notes.json"), "{\"relationships\":[],\"permissions\":[],\"runtimeNotes\":[]}");
    }
    void run(boolean strict, boolean verify) throws Exception {
        ApiReviewGenerator.generate("example", temp.resolve("src"), temp.resolve("sql"), temp.resolve("notes.json"), temp.resolve("out"), strict, verify);
    }
    @Test void deterministicCatalogUsesRealSourcesAndDoesNotEmitDataOrPublicContract() throws Exception {
        fixture(); run(false, false);
        Path review = temp.resolve("out/META-INF/vireon/api-review.json");
        byte[] first = Files.readAllBytes(review); run(false, false); assertArrayEquals(first, Files.readAllBytes(review));
        var json = new ObjectMapper().readTree(first);
        assertEquals(1, json.get("internalEndpoints").size());
        assertEquals("/api/internal", json.get("internalEndpoints").get(0).get("path").asText());
        assertEquals(3, json.get("tables").get(0).get("indexes").size());
        assertEquals("表;注释", json.get("tables").get(0).get("comment").asText());
        assertFalse(new String(first).contains("sensitive-never-output"));
        assertFalse(new String(first).contains("publicRead"));
        run(false, true);
    }
    @Test void staleSourceAndMissingCatalogFailVerification() throws Exception {
        fixture(); assertThrows(IllegalArgumentException.class, () -> run(false, true));
        run(false, false); Files.writeString(temp.resolve("src/Example.java"), JAVA.replace("value", "changed"));
        assertThrows(IllegalArgumentException.class, () -> run(false, true));
    }
    @Test void operationIdOmissionAndDuplicationFail() throws Exception {
        fixture(); Files.writeString(temp.resolve("src/Example.java"), JAVA.replace("operationId=\"internal\", ", ""));
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("src/Example.java"), JAVA.replace("publicRead", "internal"));
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void unknownModelDdlFailsRatherThanEmittingIncompleteTables() throws Exception {
        fixture(); Files.writeString(temp.resolve("sql/V2__alter.sql"), "ALTER TABLE app.fact ALTER COLUMN name TYPE text;");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void versionedMigrationsAggregateAddedColumnsAndNullability() throws Exception {
        fixture();
        Files.writeString(temp.resolve("sql/V2__add_mobile.sql"), """
                ALTER TABLE app.fact ADD COLUMN mobile varchar(64) NOT NULL UNIQUE;
                COMMENT ON COLUMN app.fact.mobile IS '手机号';
                """);
        Files.writeString(temp.resolve("sql/V3__allow_missing_name.sql"), """
                ALTER TABLE app.fact ALTER COLUMN name DROP NOT NULL;
                ALTER TABLE app.fact ALTER COLUMN mobile SET NOT NULL;
                """);
        run(false, false);
        var table = new ObjectMapper().readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-review.json")))
                .get("tables").get(0);
        assertEquals(List.of("id", "name", "mobile"), java.util.stream.StreamSupport.stream(table.get("columns").spliterator(), false)
                .map(column -> column.get("name").asText()).toList());
        assertTrue(table.get("columns").get(1).get("nullable").asBoolean());
        assertFalse(table.get("columns").get(2).get("nullable").asBoolean());
        assertEquals("手机号", table.get("columns").get(2).get("comment").asText());
        assertEquals(4, table.get("indexes").size());
    }
    @Test void alterRequiresExistingTableAndColumnAndRejectsDuplicateColumn() throws Exception {
        fixture(); Files.writeString(temp.resolve("sql/V2__bad.sql"), "ALTER TABLE app.missing ADD COLUMN value text;");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("sql/V2__bad.sql"), "ALTER TABLE app.fact ALTER COLUMN missing DROP NOT NULL;");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("sql/V2__bad.sql"), "ALTER TABLE app.fact ADD COLUMN name text;");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void unknownColumnAndMalformedSqlFail() throws Exception {
        fixture(); Files.writeString(temp.resolve("sql/V1__fact.sql"), SQL + "COMMENT ON COLUMN app.fact.missing IS 'bad';");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("sql/V1__fact.sql"), SQL + "/* open");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void notesDuplicateKeysUnknownFieldsAndReferencesFail() throws Exception {
        fixture(); Files.writeString(temp.resolve("notes.json"), "{\"relationships\":[],\"permissions\":[],\"runtimeNotes\":[],\"permissions\":[]}");
        assertThrows(Exception.class, () -> run(false, false));
        Files.writeString(temp.resolve("notes.json"), "{\"relationships\":[],\"permissions\":[],\"runtimeNotes\":[],\"other\":[]}");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("notes.json"), "{\"relationships\":[{\"fromTable\":\"app.fact\",\"fromColumn\":\"missing\",\"toTable\":\"app.fact\",\"toColumn\":\"id\",\"description\":\"test\"}],\"permissions\":[],\"runtimeNotes\":[]}");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void inheritedControllersAndMultipleRoutesFailClosed() throws Exception {
        fixture(); Files.writeString(temp.resolve("src/Example.java"), JAVA.replace("class Example", "class Example extends Base"));
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("src/Example.java"), JAVA.replace("@PostMapping(\"/internal\")", "@PostMapping({\"/internal\",\"/alias\"})"));
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void noGitExplicitlyUnavailableAndStrictFails() throws Exception {
        fixture(); run(false, false);
        var history = new ObjectMapper().readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-git-history.json")));
        assertEquals("unavailable", history.get("operations").get(0).get("state").asText());
        assertTrue(history.get("operations").get(0).get("timestamp").isNull());
        assertThrows(IllegalArgumentException.class, () -> run(true, false));
    }
    @Test void committedHistoryTracksMethodRangeNotUnrelatedMethodCommit() throws Exception {
        fixture(); git("init"); git("config", "user.name", "Catalog Test"); git("config", "user.email", "catalog@example.invalid");
        git("add", "src/Example.java", "sql/V1__fact.sql", "notes.json"); git("commit", "-m", "initial");
        run(true, false); String first = historyCommit();
        Files.writeString(temp.resolve("src/Example.java"), JAVA.replace("return \"public\"", "return \"other\""));
        run(false, false); assertEquals("committed", historyState());
        git("add", "src/Example.java"); git("commit", "-m", "unrelated method");
        run(true, false); assertEquals(first, historyCommit());
    }
    @Test void renameAndShallowHistoryDegradeAndStrictFails() throws Exception {
        fixture(); git("init"); git("config", "user.name", "Catalog Test"); git("config", "user.email", "catalog@example.invalid");
        git("add", "src/Example.java", "sql/V1__fact.sql", "notes.json"); git("commit", "-m", "initial");
        git("mv", "src/Example.java", "src/Renamed.java"); git("commit", "-m", "rename");
        run(false, false); assertEquals("unavailable", historyState());
        assertThrows(IllegalArgumentException.class, () -> run(true, false));
        Files.writeString(temp.resolve(".git/shallow"), git("rev-parse", "HEAD").strip() + "\n");
        run(false, false); assertEquals("unavailable", historyState());
        Files.writeString(temp.resolve("src/Renamed.java"), JAVA.replace("value", "dirty"));
        run(false, false); assertEquals("dirty", historyState());
    }
    @Test void oversizedNotesFail() throws Exception {
        fixture(); Files.writeString(temp.resolve("notes.json"), " ".repeat(2 * 1024 * 1024 + 1));
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void permissionAndTextProtocolBoundariesAreEnforcedBeforeWriting() throws Exception {
        fixture();
        var mapper = new ObjectMapper();
        for (String code : List.of("invalid code", "x".repeat(129), "bad\0code")) {
            Files.writeString(temp.resolve("notes.json"), mapper.writeValueAsString(Map.of("relationships", List.of(), "runtimeNotes", List.of(),
                    "permissions", List.of(Map.of("code", code, "description", "test")))));
            assertThrows(Exception.class, () -> run(false, false));
        }
        for (String text : List.of("x".repeat(16001), "bad\0text")) {
            Files.writeString(temp.resolve("notes.json"), mapper.writeValueAsString(Map.of("relationships", List.of(), "permissions", List.of(),
                    "runtimeNotes", List.of(Map.of("title", "title", "description", text)))));
            assertThrows(Exception.class, () -> run(false, false));
        }
        Files.writeString(temp.resolve("notes.json"), mapper.writeValueAsString(Map.of("relationships", List.of(), "runtimeNotes", List.of(),
                "permissions", List.of(Map.of("code", "x".repeat(128), "description", "x".repeat(16000))))));
        run(false, false);
        var generated = mapper.readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-review.json")));
        assertEquals(16000, generated.get("permissions").get(0).get("description").asText().length());
    }
    @Test void generatedSqlAndSourceTextAlsoRejectNul() throws Exception {
        fixture(); Files.writeString(temp.resolve("sql/V1__fact.sql"), SQL.replace("名称", "bad\0text"));
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("sql/V1__fact.sql"), SQL);
        Files.writeString(temp.resolve("src/Example.java"), JAVA.replace("内部", "\\0"));
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void composedWebAnnotationsCannotSilentlyOmitEndpoints() throws Exception {
        fixture(); Files.writeString(temp.resolve("src/Meta.java"), "@RestController @interface Meta {} @Meta class Extra { @GetMapping(\"/extra\") String x(){return \"\";} }");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
        Files.writeString(temp.resolve("src/Meta.java"), "@RequestMapping(method=RequestMethod.GET) @interface Meta {}");
        assertThrows(IllegalArgumentException.class, () -> run(false, false));
    }
    @Test void identityWithoutPrimaryKeyIsStillNotNullable() throws Exception {
        fixture(); Files.writeString(temp.resolve("sql/V1__fact.sql"), "CREATE TABLE app.fact (id bigint GENERATED ALWAYS AS IDENTITY);");
        run(false, false);
        var model = new ObjectMapper().readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-review.json")));
        assertFalse(model.get("tables").get(0).get("columns").get(0).get("nullable").asBoolean());
    }
    @Test void removingFirstMethodAnnotationIsDirty() throws Exception {
        fixture(); String annotated = JAVA.replace("  @Hidden", "  @Deprecated\n  @Hidden");
        Files.writeString(temp.resolve("src/Example.java"), annotated);
        git("init"); git("config", "user.name", "Catalog Test"); git("config", "user.email", "catalog@example.invalid");
        git("add", "src/Example.java", "sql/V1__fact.sql", "notes.json"); git("commit", "-m", "initial");
        Files.writeString(temp.resolve("src/Example.java"), JAVA);
        run(false, false); assertEquals("dirty", historyState());
    }
    @Test void strictHistoryRejectsDirtyNonHandlerJavaMigrationsAndNotes() throws Exception {
        fixture(); Files.writeString(temp.resolve("src/Other.java"), "class Other {}\n");
        git("init"); git("config", "user.name", "Catalog Test"); git("config", "user.email", "catalog@example.invalid");
        git("add", "src/Example.java", "src/Other.java", "sql/V1__fact.sql", "notes.json"); git("commit", "-m", "initial");
        run(true, false);
        for (String path : List.of("src/Other.java", "sql/V1__fact.sql", "notes.json")) {
            String original = Files.readString(temp.resolve(path)); Files.writeString(temp.resolve(path), original + "\n");
            assertThrows(IllegalArgumentException.class, () -> run(true, false)); Files.writeString(temp.resolve(path), original);
        }
    }
    @Test void outputCannotOverwriteNotesOrFollowLinks() throws Exception {
        fixture(); Path nestedNotes = temp.resolve("out/META-INF/vireon/api-review.json");
        Files.createDirectories(nestedNotes.getParent()); Files.copy(temp.resolve("notes.json"), nestedNotes);
        assertThrows(IllegalArgumentException.class, () -> ApiReviewGenerator.generate("example", temp.resolve("src"), temp.resolve("sql"), nestedNotes, temp.resolve("out"), false, false));
        Path real = Files.createDirectories(temp.resolve("real"));
        if (System.getProperty("os.name").startsWith("Windows")) {
            var process = new ProcessBuilder("cmd", "/c", "mklink", "/J", temp.resolve("linked").toString(), real.toString()).redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes()); assertEquals(0, process.waitFor(), log);
        } else Files.createSymbolicLink(temp.resolve("linked"), real);
        assertThrows(IllegalArgumentException.class, () -> ApiReviewGenerator.generate("example", temp.resolve("src"), temp.resolve("sql"), temp.resolve("notes.json"), temp.resolve("linked/out"), false, false));
    }
    @Test void strictHistoryRejectsDeletedDataOnlyMigration() throws Exception {
        fixture(); Path migration = temp.resolve("sql/V2__data.sql");
        Files.writeString(migration, "INSERT INTO app.fact(name) VALUES ('example');\n");
        git("init"); git("config", "user.name", "Catalog Test"); git("config", "user.email", "catalog@example.invalid");
        git("add", "src/Example.java", "sql/V1__fact.sql", "sql/V2__data.sql", "notes.json"); git("commit", "-m", "initial");
        run(true, false); Files.delete(migration);
        assertThrows(IllegalArgumentException.class, () -> run(true, false));
    }
    @Test void strictHistoryRejectsDeletedNonHandlerJava() throws Exception {
        fixture(); Path ordinary = temp.resolve("src/Ordinary.java");
        Files.writeString(ordinary, "class Ordinary {}\n");
        git("init"); git("config", "user.name", "Catalog Test"); git("config", "user.email", "catalog@example.invalid");
        git("add", "src/Example.java", "src/Ordinary.java", "sql/V1__fact.sql", "notes.json"); git("commit", "-m", "initial");
        run(true, false); Files.delete(ordinary);
        assertThrows(IllegalArgumentException.class, () -> run(true, false));
    }
    @Test void longQuotedSqlLiteralsDoNotOverflowAndKeepDefaultsPrivate() throws Exception {
        fixture();
        String literal="a''".repeat(4000);
        Files.writeString(temp.resolve("sql/V1__fact.sql"),"CREATE TABLE app.fact (name text DEFAULT '"+literal+"'); CREATE UNIQUE INDEX only_special ON app.fact(name) WHERE name = '"+literal+"'; COMMENT ON COLUMN app.fact.name IS '"+literal+"';");
        run(false,false);
        var model=new ObjectMapper().readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-review.json")));
        assertEquals("name = '"+literal+"'",model.path("tables").get(0).path("indexes").get(0).path("predicate").asText());
        assertFalse(model.path("tables").get(0).path("columns").get(0).has("default"));
        Files.writeString(temp.resolve("sql/V1__fact.sql"),"CREATE TABLE app.fact (name text DEFAULT '"+literal+");");
        assertThrows(IllegalArgumentException.class,()->run(false,false));
    }
    private String historyState() throws Exception { return new ObjectMapper().readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-git-history.json"))).get("operations").get(0).get("state").asText(); }
    private String historyCommit() throws Exception { return new ObjectMapper().readTree(Files.readAllBytes(temp.resolve("out/META-INF/vireon/api-git-history.json"))).get("operations").get(0).get("commit").asText(); }
    private String git(String... args) throws Exception {
        var command = new ArrayList<>(List.of("git")); command.addAll(List.of(args));
        var p = new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()); assertEquals(0, p.waitFor(), output); return output;
    }
}
