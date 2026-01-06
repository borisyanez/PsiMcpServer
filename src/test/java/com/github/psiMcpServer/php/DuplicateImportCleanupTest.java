package com.github.psiMcpServer.php;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for duplicate import cleanup logic.
 * When a class is moved from global namespace to a named namespace,
 * we need to clean up duplicate use statements that may result.
 */
public class DuplicateImportCleanupTest {

    // ========== Duplicate Detection Tests ==========

    @Test
    public void testDetectDuplicateImports_GlobalAndNamespaced() {
        String code = """
            <?php
            namespace App\\Services;

            use Cart;
            use Entities\\Cart;

            class OrderService {}
            """;

        List<DuplicateInfo> duplicates = findDuplicateImports(code, "Cart");

        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.get(0).globalImport).isEqualTo("use Cart;");
        assertThat(duplicates.get(0).namespacedImport).isEqualTo("use Entities\\Cart;");
    }

    @Test
    public void testDetectDuplicateImports_NoDuplicates() {
        String code = """
            <?php
            namespace App\\Services;

            use Entities\\Cart;
            use App\\Models\\User;

            class OrderService {}
            """;

        List<DuplicateInfo> duplicates = findDuplicateImports(code, "Cart");

        assertThat(duplicates).isEmpty();
    }

    @Test
    public void testDetectDuplicateImports_OnlyGlobal() {
        String code = """
            <?php
            namespace App\\Services;

            use Cart;

            class OrderService {}
            """;

        List<DuplicateInfo> duplicates = findDuplicateImports(code, "Cart");

        assertThat(duplicates).isEmpty();
    }

    @Test
    public void testDetectDuplicateImports_OnlyNamespaced() {
        String code = """
            <?php
            namespace App\\Services;

            use Entities\\Cart;

            class OrderService {}
            """;

        List<DuplicateInfo> duplicates = findDuplicateImports(code, "Cart");

        assertThat(duplicates).isEmpty();
    }

    @Test
    public void testDetectDuplicateImports_MultipleClasses() {
        String code = """
            <?php
            namespace App\\Services;

            use Cart;
            use Product;
            use Entities\\Cart;
            use Entities\\Product;

            class OrderService {}
            """;

        List<DuplicateInfo> cartDuplicates = findDuplicateImports(code, "Cart");
        List<DuplicateInfo> productDuplicates = findDuplicateImports(code, "Product");

        assertThat(cartDuplicates).hasSize(1);
        assertThat(productDuplicates).hasSize(1);
    }

    // ========== Duplicate Removal Tests ==========

    @Test
    public void testRemoveDuplicateImport_RemovesGlobal() {
        String code = """
            <?php
            namespace App\\Services;

            use Cart;
            use Entities\\Cart;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "Cart");

        assertThat(result).doesNotContain("use Cart;");
        assertThat(result).contains("use Entities\\Cart;");
    }

    @Test
    public void testRemoveDuplicateImport_PreservesOtherImports() {
        String code = """
            <?php
            namespace App\\Services;

            use Cart;
            use App\\Models\\User;
            use Entities\\Cart;
            use App\\Models\\Order;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "Cart");

        assertThat(result).doesNotContain("use Cart;");
        assertThat(result).contains("use Entities\\Cart;");
        assertThat(result).contains("use App\\Models\\User;");
        assertThat(result).contains("use App\\Models\\Order;");
    }

    @Test
    public void testRemoveDuplicateImport_NoChange_WhenNoDuplicates() {
        String code = """
            <?php
            namespace App\\Services;

            use Entities\\Cart;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "Cart");

        assertThat(result).isEqualTo(code);
    }

    @Test
    public void testRemoveDuplicateImport_HandlesWhitespace() {
        String code = """
            <?php
            namespace App\\Services;

            use   Cart  ;
            use Entities\\Cart;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "Cart");

        assertThat(result).doesNotContain("use   Cart  ;");
        assertThat(result).contains("use Entities\\Cart;");
    }

    // ========== Edge Case Tests ==========

    @Test
    public void testRemoveDuplicateImport_SimilarNames_DoesNotRemovePartialMatch() {
        String code = """
            <?php
            namespace App\\Services;

            use Cart;
            use CartItem;
            use Entities\\Cart;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "Cart");

        assertThat(result).doesNotContain("use Cart;");
        assertThat(result).contains("use CartItem;");
        assertThat(result).contains("use Entities\\Cart;");
    }

    @Test
    public void testRemoveDuplicateImport_ClassNameInMiddle() {
        String code = """
            <?php
            namespace App\\Services;

            use User;
            use App\\Models\\User;
            use UserService;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "User");

        assertThat(result).doesNotContain("use User;");
        assertThat(result).contains("use App\\Models\\User;");
        assertThat(result).contains("use UserService;");
    }

    @Test
    public void testRemoveDuplicateImport_WithAlias() {
        // If using alias, both should be kept as they serve different purposes
        String code = """
            <?php
            namespace App\\Services;

            use Cart;
            use Entities\\Cart as EntityCart;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "Cart");

        // With alias, this is NOT a duplicate - both should remain
        assertThat(result).contains("use Cart;");
        assertThat(result).contains("use Entities\\Cart as EntityCart;");
    }

    @Test
    public void testRemoveDuplicateImport_LeadingBackslash() {
        String code = """
            <?php
            namespace App\\Services;

            use \\Cart;
            use \\Entities\\Cart;

            class OrderService {}
            """;

        String result = removeDuplicateImport(code, "Cart");

        // Should handle leading backslash
        assertThat(countOccurrences(result, "use")).isEqualTo(1);
    }

    // ========== Batch Cleanup Tests ==========

    @Test
    public void testBatchCleanup_MultipleFiles() {
        List<String> files = List.of(
            """
            <?php
            namespace App\\Services;
            use Cart;
            use Entities\\Cart;
            class ServiceA {}
            """,
            """
            <?php
            namespace App\\Controllers;
            use Cart;
            use Entities\\Cart;
            class ControllerA {}
            """,
            """
            <?php
            namespace App\\Models;
            use Entities\\Cart;
            class ModelA {}
            """
        );

        List<String> results = batchCleanup(files, "Cart");

        // First two files should have Cart removed
        assertThat(results.get(0)).doesNotContain("use Cart;");
        assertThat(results.get(0)).contains("use Entities\\Cart;");

        assertThat(results.get(1)).doesNotContain("use Cart;");
        assertThat(results.get(1)).contains("use Entities\\Cart;");

        // Third file had no duplicate
        assertThat(results.get(2)).contains("use Entities\\Cart;");
    }

    @Test
    public void testBatchCleanup_CountsAffectedFiles() {
        List<String> files = List.of(
            """
            <?php
            use Cart;
            use Entities\\Cart;
            """,
            """
            <?php
            use Entities\\Cart;
            """,
            """
            <?php
            use Cart;
            use Entities\\Cart;
            """
        );

        int affectedCount = countFilesWithDuplicates(files, "Cart");

        assertThat(affectedCount).isEqualTo(2);
    }

    // ========== Helper Methods (mirror ManualReferenceUpdater logic) ==========

    private List<DuplicateInfo> findDuplicateImports(String code, String className) {
        List<DuplicateInfo> duplicates = new ArrayList<>();

        // Pattern for global namespace import: use ClassName;
        Pattern globalPattern = Pattern.compile("use\\s+\\\\?" + Pattern.quote(className) + "\\s*;");

        // Pattern for namespaced import: use SomeNamespace\ClassName;
        Pattern namespacedPattern = Pattern.compile("use\\s+\\\\?[A-Za-z0-9_\\\\]+\\\\" + Pattern.quote(className) + "\\s*;");

        Matcher globalMatcher = globalPattern.matcher(code);
        Matcher namespacedMatcher = namespacedPattern.matcher(code);

        String globalImport = null;
        String namespacedImport = null;

        if (globalMatcher.find()) {
            globalImport = globalMatcher.group();
        }

        if (namespacedMatcher.find()) {
            namespacedImport = namespacedMatcher.group();
        }

        // Only a duplicate if BOTH exist
        if (globalImport != null && namespacedImport != null) {
            // Check that namespaced doesn't have an alias
            if (!namespacedImport.contains(" as ")) {
                duplicates.add(new DuplicateInfo(globalImport, namespacedImport));
            }
        }

        return duplicates;
    }

    private String removeDuplicateImport(String code, String className) {
        List<DuplicateInfo> duplicates = findDuplicateImports(code, className);

        if (duplicates.isEmpty()) {
            return code;
        }

        String result = code;
        for (DuplicateInfo dup : duplicates) {
            // Remove the global import line, including newline
            result = result.replace(dup.globalImport + "\n", "");
            // Also try without newline (in case it's the last line)
            result = result.replace(dup.globalImport, "");
        }

        return result;
    }

    private List<String> batchCleanup(List<String> files, String className) {
        List<String> results = new ArrayList<>();
        for (String file : files) {
            results.add(removeDuplicateImport(file, className));
        }
        return results;
    }

    private int countFilesWithDuplicates(List<String> files, String className) {
        int count = 0;
        for (String file : files) {
            if (!findDuplicateImports(file, className).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }

    // ========== Helper Records ==========

    record DuplicateInfo(String globalImport, String namespacedImport) {}
}
