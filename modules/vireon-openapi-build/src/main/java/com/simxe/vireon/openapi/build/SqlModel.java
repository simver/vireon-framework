package com.simxe.vireon.openapi.build;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Deliberately bounded PostgreSQL migration grammar; unknown model DDL is fatal. */
final class SqlModel {
    private static final String ID = "[a-z_][a-z0-9_]*";
    private static final String TABLE = ID + "\\." + ID;
    private static final String LITERAL = "(?:-?[0-9]+|TRUE|FALSE|'(?:[^'\\\\\\r\\n]|'')*+')";
    private final Map<String, Map<String, Object>> tables = new TreeMap<>();

    static List<Map<String, Object>> read(List<Path> files) throws Exception {
        var parser = new SqlModel();
        var ordered = new TreeMap<java.math.BigInteger, Path>();
        for (var file : files) {
            var name = Pattern.compile("V([0-9]+)__[A-Za-z0-9_-]+\\.sql").matcher(file.getFileName().toString());
            if (!name.matches() || ordered.put(new java.math.BigInteger(name.group(1)), file) != null)
                throw new IllegalArgumentException("INTEGER_VERSIONED_MIGRATION_REQUIRED");
        }
        for (var file : ordered.values()) for (var statement : split(Files.readString(file), ';')) parser.statement(statement.strip());
        return new ArrayList<>(parser.tables.values());
    }

    private void statement(String sql) {
        if (sql.isBlank()) return;
        Matcher m = match("CREATE TABLE (" + TABLE + ")\\s*\\((.*)\\)", sql);
        if (m.matches()) {
            String name = m.group(1);
            if (tables.containsKey(name)) fail();
            var table = new LinkedHashMap<String, Object>();
            table.put("name", name); table.put("comment", "");
            table.put("columns", new ArrayList<Map<String, Object>>()); table.put("indexes", new ArrayList<Map<String, Object>>());
            tables.put(name, table);
            for (String part : split(m.group(2), ',')) tableElement(table, part.strip());
            return;
        }
        m = match("ALTER TABLE (" + TABLE + ") ADD COLUMN (.*)", sql);
        if (m.matches()) {
            column(table(m.group(1)), m.group(2).strip());
            return;
        }
        m = match("ALTER TABLE (" + TABLE + ") ALTER COLUMN (" + ID + ") (SET|DROP) NOT NULL", sql);
        if (m.matches()) {
            findColumn(table(m.group(1)), m.group(2)).put("nullable", m.group(3).equalsIgnoreCase("DROP"));
            return;
        }
        m = match("CREATE (UNIQUE )?INDEX (" + ID + ") ON (" + TABLE + ")\\s*\\(([^()]*)\\)(?:\\s+WHERE ("+ID+")\\s*=\\s*("+LITERAL+"))?", sql);
        if (m.matches()) {
            var target=table(m.group(3));
            index(target, m.group(2), m.group(1) != null, m.group(4));
            if(m.group(5)!=null) {
                findColumn(target,m.group(5));
                indexes(target).getLast().put("predicate",m.group(5)+" = "+m.group(6));
            }
            return;
        }
        m = match("COMMENT ON (TABLE|COLUMN) (" + TABLE + "(?:\\." + ID + ")?) IS ('(?:[^']|'')*+'|NULL)", sql);
        if (m.matches()) {
            String target = m.group(2);
            String comment = m.group(3).equalsIgnoreCase("NULL") ? "" : m.group(3).substring(1, m.group(3).length() - 1).replace("''", "'");
            if (m.group(1).equalsIgnoreCase("TABLE")) table(target).put("comment", comment);
            else {
                int dot = target.lastIndexOf('.');
                findColumn(table(target.substring(0, dot)), target.substring(dot + 1)).put("comment", comment);
            }
            return;
        }
        // These statements do not change the table/column/index model; never emit their text or values.
        if (match("(?:INSERT INTO|UPDATE|DELETE FROM|GRANT|REVOKE)\\s+.*", sql).matches()) return;
        throw new IllegalArgumentException("UNSUPPORTED_SQL_STATEMENT");
    }

    private void tableElement(Map<String, Object> table, String sql) {
        Matcher constraint = match("(?:CONSTRAINT (" + ID + ") )?(UNIQUE|PRIMARY KEY)\\s*\\((.*)\\)", sql);
        if (constraint.matches()) {
            String suffix = constraint.group(2).equalsIgnoreCase("PRIMARY KEY") ? "_pkey" : "_" + String.join("_", split(constraint.group(3), ',').stream().map(String::strip).toList()) + "_key";
            String name = constraint.group(1) == null ? shortName(table) + suffix : constraint.group(1);
            index(table, name, true, constraint.group(3));
            if (constraint.group(2).equalsIgnoreCase("PRIMARY KEY"))
                for (String c : split(constraint.group(3), ',')) findColumn(table, c.strip()).put("nullable", false);
            return;
        }
        column(table, sql);
    }

    private void column(Map<String, Object> table, String sql) {
        Matcher m = match("(" + ID + ")\\s+(bigint|smallint|integer|uuid|boolean|jsonb|text|varchar\\([0-9]+\\)|timestamptz(?:\\([0-9]+\\))?)(.*)", sql);
        if (!m.matches()) fail();
        String name = m.group(1);
        if (columns(table).stream().anyMatch(c -> c.get("name").equals(name))) fail();
        String rest=m.group(3).strip();
        var modifiers=new HashSet<String>();
        while(!rest.isEmpty()) {
            Matcher modifier=match("^(GENERATED ALWAYS AS IDENTITY|PRIMARY KEY|NOT NULL|UNIQUE|DEFAULT (?:DATE_TRUNC\\('MILLISECONDS', CLOCK_TIMESTAMP\\(\\)\\)|"+LITERAL+"))(?=\\s|$)",rest);
            if(!modifier.find())fail();
            String key=modifier.group(1).toUpperCase(Locale.ROOT);
            if(key.startsWith("DEFAULT "))key="DEFAULT";
            if(!modifiers.add(key))fail();
            rest=rest.substring(modifier.end()).strip();
        }
        boolean identity=modifiers.contains("GENERATED ALWAYS AS IDENTITY"),primary=modifiers.contains("PRIMARY KEY");
        if(identity && modifiers.contains("DEFAULT"))fail();
        boolean unique=primary || modifiers.contains("UNIQUE");
        boolean nullable=!identity && !primary && !modifiers.contains("NOT NULL");
        var c = new LinkedHashMap<String, Object>();
        c.put("name", name); c.put("type", m.group(2).toLowerCase(Locale.ROOT)); c.put("nullable", nullable); c.put("comment", "");
        columns(table).add(c);
        if (unique) index(table, shortName(table) + (primary ? "_pkey" : "_" + name + "_key"), true, name);
    }

    private void index(Map<String, Object> table, String name, boolean unique, String raw) {
        if (name.length() > 63) fail();
        if (indexes(table).stream().anyMatch(i -> i.get("name").equals(name))) fail();
        var names = new ArrayList<String>();
        for (String item : split(raw, ',')) {
            Matcher m = match("(" + ID + ")(?: (ASC|DESC))?", item.strip());
            if (!m.matches()) fail();
            findColumn(table, m.group(1));
            names.add(m.group(1) + (m.group(2) == null ? "" : " " + m.group(2).toUpperCase(Locale.ROOT)));
        }
        if (names.isEmpty()) fail();
        var index = new LinkedHashMap<String, Object>();
        index.put("name", name); index.put("unique", unique); index.put("columns", names);
        indexes(table).add(index);
    }

    private Map<String, Object> table(String name) {
        var table = tables.get(name); if (table == null) fail(); return table;
    }
    private static String shortName(Map<String, Object> table) { String name = table.get("name").toString(); return name.substring(name.indexOf('.') + 1); }
    private static Map<String, Object> findColumn(Map<String, Object> table, String name) {
        return columns(table).stream().filter(c -> c.get("name").equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_SQL_COLUMN"));
    }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> columns(Map<String, Object> t) { return (List<Map<String, Object>>) t.get("columns"); }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> indexes(Map<String, Object> t) { return (List<Map<String, Object>>) t.get("indexes"); }
    private static Matcher match(String regex, String value) { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(value); }
    private static void fail() { throw new IllegalArgumentException("UNSUPPORTED_OR_DUPLICATE_SQL_MODEL"); }

    /** Quote/comment aware separator, rejects unterminated text rather than skipping input. */
    static List<String> split(String sql, char separator) {
        List<String> parts = new ArrayList<>();
        var current = new StringBuilder();
        boolean quote = false; int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!quote && c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                while (i < sql.length() && sql.charAt(i) != '\n') i++;
                current.append(' '); continue;
            }
            if (!quote && c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2); if (end < 0) fail();
                if (sql.substring(i + 2, end).contains("/*")) fail();
                i = end + 1; current.append(' '); continue;
            }
            if (c == '\'') {
                if (quote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') { current.append("''"); i++; continue; }
                quote = !quote;
            }
            if (!quote) {
                if (c == '(') depth++;
                if (c == ')') depth--;
                if (depth < 0) fail();
                if (c == separator && depth == 0) { parts.add(current.toString()); current.setLength(0); continue; }
            }
            current.append(c);
        }
        if (quote || depth != 0) fail();
        if (!current.toString().isBlank()) parts.add(current.toString());
        return parts;
    }
}
