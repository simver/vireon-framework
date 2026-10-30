package com.simxe.vireon.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.simxe.vireon.testsupport.VireonArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class VireonFrameworkArchitectureTests {

    private static final List<String> PUBLISHED_MODULES = List.of(
            "build/vireon-parent",
            "modules/vireon-core",
            "modules/vireon-web",
            "modules/vireon-security",
            "modules/vireon-observability",
            "modules/vireon-test-support",
            "modules/vireon-openapi",
            "modules/vireon-openapi-build",
            "starters/vireon-starter-service");

    private static final List<String> REACTOR_MODULES = Stream.concat(
                    PUBLISHED_MODULES.stream(), Stream.of("tests/vireon-framework-architecture-tests"))
            .toList();

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new DoNotIncludeTests())
            .importPackages("com.simxe.vireon");

    @Test
    void frameworkMustNotDependOnProjectImplementations() {
        VireonArchitectureRules.noDependencies(
                        "com.simxe.vireon..",
                        "com.simxe.vireon.platform..",
                        "com.simxe.vireon.iam..",
                        "com.simxe.vireon.qbc..",
                        "com.simxe.vireon.gateway..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void reactorAndPublicationWhitelistMustStayExact() throws Exception {
        assertThat(modules(root().resolve("pom.xml"))).containsExactlyElementsOf(REACTOR_MODULES);
        assertThat(Files.readAllLines(root().resolve("ops/maven/framework-modules.txt")))
                .containsExactlyElementsOf(PUBLISHED_MODULES);
        assertThat(modules(root().resolve("ops/maven/framework-release/pom.xml")))
                .containsExactlyElementsOf(PUBLISHED_MODULES);
    }

    @Test
    void serviceStarterMustStayMinimal() throws IOException {
        String pom = Files.readString(root().resolve("starters/vireon-starter-service/pom.xml"));

        assertThat(pom)
                .doesNotContain("vireon-data-mybatis")
                .doesNotContain("vireon-redis")
                .doesNotContain("vireon-audit")
                .doesNotContain("vireon-crypto")
                .doesNotContain("vireon-file")
                .doesNotContain("vireon-events")
                .doesNotContain("vireon-openapi")
                .doesNotContain("spring-boot-starter-security");
    }

    @Test
    void productionApiMustNotIntroduceTenantModel() throws IOException {
        Path targetRoot = root().resolve("target");
        try (Stream<Path> files = Files.walk(root())) {
            List<Path> violations = files.filter(path -> !path.startsWith(targetRoot))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(VireonFrameworkArchitectureTests::containsTenantTerm)
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private static boolean containsTenantTerm(Path path) {
        try {
            return Files.readString(path).toLowerCase().contains("tenant");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private static List<String> modules(Path pom) throws Exception {
        Element project = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(pom.toFile())
                .getDocumentElement();
        NodeList nodes = project.getElementsByTagName("module");
        Path reactorRoot = pom.getParent();
        return java.util.stream.IntStream.range(0, nodes.getLength())
                .mapToObj(index -> reactorRoot.resolve(nodes.item(index).getTextContent().trim()).normalize())
                .map(root()::relativize)
                .map(path -> path.toString().replace('\\', '/'))
                .toList();
    }

    private static Path root() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
