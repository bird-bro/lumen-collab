package org.birdbro.lumen.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module architecture guard (lumen-core-registration-kernel task 1.1; spec delta
 * service-topology "Shared Library Boundary" - Library is not runnable / HTTP surface
 * ownership). Static source-tree assertions, so they run in the normal test phase
 * WITHOUT packaging the jar:
 *
 * <ol>
 *   <li>the aggregator declares {@code lumen-core} and the module pom carries NO
 *       spring-boot-maven-plugin (no repackage goal can ever produce an executable
 *       jar);</li>
 *   <li>main sources declare NO {@code @SpringBootApplication} (non-executable
 *       library - the test host lives in test sources only, design D9);</li>
 *   <li>main sources declare NO {@code @RestController}/{@code @Controller} - the
 *       HTTP surface belongs to each consuming department service.</li>
 * </ol>
 *
 * <p>RED evidence (task 1.1): before the module skeleton existed the test could not
 * even compile (module absent from the reactor) - the designed failing-first signal.
 * On an empty module the guard passes trivially and keeps its teeth for every later
 * batch: any class introduced with a boot entry point or a controller fails here.</p>
 *
 * @author birdbro
 */
class KernelModuleArchitectureTests {

    private static final Path MODULE_DIR = moduleDirectory();
    private static final Pattern FORBIDDEN_ANNOTATIONS = Pattern.compile(
            "@SpringBootApplication|@RestController|@Controller\\b");

    /** Resolves lumen-sev/lumen-core walking up from the surefire working directory. */
    private static Path moduleDirectory() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (dir.getFileName() != null && "lumen-core".equals(dir.getFileName().toString())
                    && dir.resolve("pom.xml").toFile().exists()) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "lumen-core module directory not found walking up from "
                + Paths.get("").toAbsolutePath());
    }

    @Test
    void aggregatorDeclaresTheModuleWithoutBootRepackage() throws IOException {
        Path aggregatorPom = MODULE_DIR.getParent().resolve("pom.xml");
        List<String> lines = Files.readAllLines(aggregatorPom);
        assertTrue(lines.stream().anyMatch(line -> line.trim().equals("<module>lumen-core</module>")),
                "the aggregator must declare <module>lumen-core</module>");

        List<String> modulePom = Files.readAllLines(MODULE_DIR.resolve("pom.xml"));
        String joined = String.join("\n", modulePom);
        assertFalse(joined.contains("spring-boot-maven-plugin"),
                "a shared library must not carry the spring-boot repackage plugin");
    }

    @Test
    void mainSourcesDeclareNoBootApplicationAndNoControllers() throws IOException {
        Path mainJava = MODULE_DIR.resolve("src/main/java");
        assertTrue(Files.isDirectory(mainJava), "main sources tree must exist");
        try (Stream<Path> sources = Files.walk(mainJava)) {
            List<Path> javaFiles = sources
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(java.util.stream.Collectors.toList());
            for (Path source : javaFiles) {
                String text = new String(Files.readAllBytes(source));
                assertFalse(FORBIDDEN_ANNOTATIONS.matcher(text).find(),
                        "shared-library main source must not declare a boot entry point "
                        + "or a controller: " + source);
            }
        }
    }

    @Test
    void canonicalKernelDdlIsPresent() {
        Path ddl = MODULE_DIR.resolve(
                "src/main/resources/db/kernel/V1__exam_kernel_registration.sql");
        assertTrue(java.nio.file.Files.exists(ddl),
                "the canonical kernel DDL (design D2 copy-from source) must exist: " + ddl);
    }
}
