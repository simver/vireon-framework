package com.simxe.vireon.openapi.build;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import javax.tools.*;

/** Parses syntax with the supported JDK compiler; never loads service classes. */
final class JavaEndpoints {
    record Endpoint(String operationId, String handler, String method, String path,
                    String summary, boolean hidden, Path file, int start, int end) {}

    static List<Endpoint> read(List<Path> files) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalArgumentException("JDK_COMPILER_REQUIRED");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var result = new ArrayList<Endpoint>();
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "25"), null, manager.getJavaFileObjectsFromPaths(files));
            var units = task.parse();
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : units) {
                new TreeScanner<Void, String>() {
                    @Override public Void visitClass(ClassTree type, String enclosing) {
                        String name = enclosing == null ? (unit.getPackageName() == null ? "" : unit.getPackageName() + ".")
                                + type.getSimpleName() : enclosing + "$" + type.getSimpleName();
                        var annotations = type.getModifiers().getAnnotations();
                        if (type.getKind() == Tree.Kind.ANNOTATION_TYPE && (has(annotations, "RestController")
                                || has(annotations, "Controller") || mapping(annotations) != null))
                            throw new IllegalArgumentException("COMPOSED_WEB_ANNOTATION_UNSUPPORTED");
                        boolean controller = has(annotations, "RestController") || has(annotations, "Controller");
                        if (controller) {
                            if (type.getExtendsClause() != null || !type.getImplementsClause().isEmpty())
                                throw new IllegalArgumentException("INHERITED_CONTROLLER_UNSUPPORTED");
                            String prefix = path(annotations);
                            Set<String> names = new HashSet<>();
                            for (Tree member : type.getMembers()) {
                                if (!(member instanceof MethodTree method)) continue;
                                var anns = method.getModifiers().getAnnotations();
                                var mapping = mapping(anns);
                                if (mapping == null) continue;
                                if (!names.add(method.getName().toString())) throw new IllegalArgumentException("OVERLOADED_HANDLER");
                                String id = attribute(anns, "Operation", "operationId", "");
                                if (!id.matches("[A-Za-z0-9_.-]{1,160}"))
                                    throw new IllegalArgumentException("EXPLICIT_OPERATION_ID_REQUIRED:" + name + "#" + method.getName());
                                if (!(name + "#" + method.getName()).matches("[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+"))
                                    throw new IllegalArgumentException("INVALID_HANDLER_IDENTITY");
                                String verb = simple(mapping.getAnnotationType().toString()).replace("Mapping", "").toUpperCase(Locale.ROOT);
                                if (verb.equals("REQUEST")) {
                                    var value = attributes(mapping).get("method");
                                    if (value == null) throw new IllegalArgumentException("EXPLICIT_HTTP_METHOD_REQUIRED");
                                    String token = value.toString();
                                    if (!token.matches("(?:RequestMethod\\.)?(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)"))
                                        throw new IllegalArgumentException("SINGLE_HTTP_METHOD_REQUIRED");
                                    verb = simple(token);
                                }
                                String route = prefix + path(anns);
                                if (!route.startsWith("/") || route.contains("//") || route.contains("${"))
                                    throw new IllegalArgumentException("LITERAL_CANONICAL_ROUTE_REQUIRED");
                                long start = positions.getStartPosition(unit, method);
                                long end = positions.getEndPosition(unit, method);
                                result.add(new Endpoint(id, name + "#" + method.getName(), verb, route,
                                        attribute(anns, "Operation", "summary", ""),
                                        has(annotations, "Hidden") || has(anns, "Hidden")
                                                || attribute(anns, "Operation", "hidden", "false").equals("true"),
                                        Path.of(unit.getSourceFile().toUri()), (int) unit.getLineMap().getLineNumber(start),
                                        (int) unit.getLineMap().getLineNumber(end - 1)));
                            }
                        }
                        return super.visitClass(type, name);
                    }
                }.scan(unit, null);
            }
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR))
            throw new IllegalArgumentException("JAVA_SYNTAX_ERROR");
        Set<String> ids = new HashSet<>();
        Set<String> handlers = new HashSet<>();
        Set<String> routes = new HashSet<>();
        for (var e : result) {
            if (!ids.add(e.operationId()) || !handlers.add(e.handler()) || !routes.add(e.method() + " " + e.path()))
                throw new IllegalArgumentException("DUPLICATE_ENDPOINT_MAPPING");
        }
        result.sort(Comparator.comparing(Endpoint::operationId));
        return result;
    }

    private static AnnotationTree mapping(List<? extends AnnotationTree> annotations) {
        AnnotationTree found = null;
        for (var annotation : annotations) {
            String name = simple(annotation.getAnnotationType().toString());
            if (!name.endsWith("Mapping")) continue;
            if (!Set.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping").contains(name)
                    || found != null) throw new IllegalArgumentException("UNSUPPORTED_MAPPING");
            found = annotation;
        }
        return found;
    }

    private static String path(List<? extends AnnotationTree> annotations) {
        var mapping = mapping(annotations);
        if (mapping == null) return "";
        var values = attributes(mapping);
        if (values.containsKey("path") && values.containsKey("value")) throw new IllegalArgumentException("AMBIGUOUS_PATH");
        return literal(values.getOrDefault("path", values.get("value")), "");
    }

    private static String attribute(List<? extends AnnotationTree> annotations, String name, String key, String fallback) {
        return annotations.stream().filter(a -> simple(a.getAnnotationType().toString()).equals(name))
                .findFirst().map(a -> literal(attributes(a).get(key), fallback)).orElse(fallback);
    }

    private static Map<String, ExpressionTree> attributes(AnnotationTree annotation) {
        var result = new HashMap<String, ExpressionTree>();
        for (var arg : annotation.getArguments()) {
            if (arg instanceof AssignmentTree assignment) result.put(assignment.getVariable().toString(), assignment.getExpression());
            else result.put("value", arg);
        }
        return result;
    }

    private static String literal(ExpressionTree tree, String fallback) {
        if (tree == null) return fallback;
        if (tree instanceof LiteralTree value) return String.valueOf(value.getValue());
        if (tree instanceof BinaryTree binary && binary.getKind() == Tree.Kind.PLUS)
            return literal(binary.getLeftOperand(), "") + literal(binary.getRightOperand(), "");
        if (tree instanceof NewArrayTree array && array.getInitializers().size() == 1) return literal(array.getInitializers().getFirst(), "");
        throw new IllegalArgumentException("ANNOTATION_LITERAL_REQUIRED");
    }

    private static boolean has(List<? extends AnnotationTree> annotations, String name) {
        return annotations.stream().anyMatch(a -> simple(a.getAnnotationType().toString()).equals(name));
    }
    private static String simple(String name) { return name.substring(name.lastIndexOf('.') + 1); }
}
