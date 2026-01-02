package com.github.psiMcpServer.php;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PHP namespace-related helper methods.
 * These tests verify the logic used in namespace updates without requiring
 * the full IntelliJ platform or PHP plugin.
 */
public class PhpNamespaceUpdateTest {

    @Test
    public void testNamespaceExtractionFromFqnWithLeadingBackslash() {
        assertThat(extractNamespace("\\App\\Services\\UserService"))
            .isEqualTo("App\\Services");
    }

    @Test
    public void testNamespaceExtractionFromFqnWithoutLeadingBackslash() {
        assertThat(extractNamespace("App\\Services\\UserService"))
            .isEqualTo("App\\Services");
    }

    @Test
    public void testNamespaceExtractionFromSingleLevelNamespace() {
        assertThat(extractNamespace("App\\UserService"))
            .isEqualTo("App");
    }

    @Test
    public void testNamespaceExtractionFromGlobalNamespace() {
        assertThat(extractNamespace("UserService"))
            .isEqualTo("");
    }

    @Test
    public void testNamespaceExtractionFromGlobalNamespaceWithBackslash() {
        assertThat(extractNamespace("\\UserService"))
            .isEqualTo("");
    }

    @Test
    public void testNamespaceExtractionFromNull() {
        assertThat(extractNamespace(null))
            .isEqualTo("");
    }

    @Test
    public void testRelativePathAdjustment_MovingDeeper_OneLevel() {
        assertThat(adjustRelativePath("../path/file.php", 1))
            .isEqualTo("../../path/file.php");
    }

    @Test
    public void testRelativePathAdjustment_MovingDeeper_TwoLevels() {
        assertThat(adjustRelativePath("../path/file.php", 2))
            .isEqualTo("../../../path/file.php");
    }

    @Test
    public void testRelativePathAdjustment_MovingShallower_OneLevel() {
        assertThat(adjustRelativePath("../path/file.php", -1))
            .isEqualTo("./path/file.php");
    }

    @Test
    public void testRelativePathAdjustment_MovingShallower_TwoLevels() {
        assertThat(adjustRelativePath("../../file.php", -2))
            .isEqualTo("./file.php");
    }

    @Test
    public void testRelativePathAdjustment_SameDepth() {
        assertThat(adjustRelativePath("../path/file.php", 0))
            .isEqualTo("../path/file.php");
    }

    @Test
    public void testRelativePathAdjustment_WithLeadingSlash() {
        assertThat(adjustRelativePath("/path/file.php", 1))
            .isEqualTo("/../path/file.php");
    }

    @Test
    public void testRelativePathAdjustment_MultipleParentRefs() {
        assertThat(adjustRelativePath("../../Models/User.php", 1))
            .isEqualTo("../../../Models/User.php");
    }

    @Test
    public void testRelativePathAdjustment_RemoveAllParentRefs() {
        assertThat(adjustRelativePath("../file.php", -1))
            .isEqualTo("./file.php");
    }

    @Test
    public void testNamespaceDepthCalculation() {
        assertThat(calculateNamespaceDepth("")).isEqualTo(0);
        assertThat(calculateNamespaceDepth("App")).isEqualTo(1);
        assertThat(calculateNamespaceDepth("App\\Services")).isEqualTo(2);
        assertThat(calculateNamespaceDepth("App\\Domain\\Services")).isEqualTo(3);
    }

    @Test
    public void testNewFqnConstruction_WithNamespace() {
        String namespace = "App\\Services";
        String className = "UserService";
        String fqn = namespace.isEmpty() ? className : namespace + "\\" + className;

        assertThat(fqn).isEqualTo("App\\Services\\UserService");
    }

    @Test
    public void testNewFqnConstruction_GlobalNamespace() {
        String namespace = "";
        String className = "UserService";
        String fqn = namespace.isEmpty() ? className : namespace + "\\" + className;

        assertThat(fqn).isEqualTo("UserService");
    }

    @Test
    public void testIsGlobalNamespaceClass() {
        // Global namespace class - FQN has no backslashes
        assertThat(isGlobalNamespaceClass("UserService")).isTrue();
        assertThat(isGlobalNamespaceClass("\\UserService")).isTrue();

        // Namespaced class
        assertThat(isGlobalNamespaceClass("App\\UserService")).isFalse();
        assertThat(isGlobalNamespaceClass("\\App\\UserService")).isFalse();
    }

    // Helper methods extracted from ManualReferenceUpdater for testing

    private String extractNamespace(String fqn) {
        if (fqn == null) return "";
        if (fqn.startsWith("\\")) {
            fqn = fqn.substring(1);
        }
        int lastSlash = fqn.lastIndexOf('\\');
        return lastSlash >= 0 ? fqn.substring(0, lastSlash) : "";
    }

    private String adjustRelativePath(String path, int depthDiff) {
        if (depthDiff > 0) {
            // Moved deeper - need more "../" to go up
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < depthDiff; i++) {
                prefix.append("../");
            }
            if (path.startsWith("/")) {
                return "/" + prefix + path.substring(1);
            } else {
                return prefix + path;
            }
        } else if (depthDiff < 0) {
            // Moved shallower - need fewer "../"
            int toRemove = -depthDiff;
            String result = path;
            for (int i = 0; i < toRemove && result.startsWith("../"); i++) {
                result = result.substring(3);
            }
            if (!result.startsWith("../") && !result.startsWith("/") && !result.startsWith("./")) {
                result = "./" + result;
            }
            return result;
        }
        return path;
    }

    private int calculateNamespaceDepth(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return 0;
        }
        return namespace.split("\\\\").length;
    }

    private boolean isGlobalNamespaceClass(String fqn) {
        if (fqn == null) return false;
        String normalizedFqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
        return !normalizedFqn.contains("\\");
    }
}
