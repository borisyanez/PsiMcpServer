package com.github.psiMcpServer.php;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for use statement generation and duplicate detection logic.
 * These tests verify the logic used in ManualReferenceUpdater without
 * requiring the full IntelliJ platform or PHP plugin.
 */
public class UseStatementTest {

    // ========== FQN Normalization Tests ==========

    @Test
    public void testNormalizeFqn_WithLeadingBackslash() {
        assertThat(normalizeFqn("\\App\\Models\\User"))
            .isEqualTo("App\\Models\\User");
    }

    @Test
    public void testNormalizeFqn_WithoutLeadingBackslash() {
        assertThat(normalizeFqn("App\\Models\\User"))
            .isEqualTo("App\\Models\\User");
    }

    @Test
    public void testNormalizeFqn_GlobalNamespace() {
        assertThat(normalizeFqn("\\User"))
            .isEqualTo("User");
    }

    @Test
    public void testNormalizeFqn_GlobalNamespaceWithoutBackslash() {
        assertThat(normalizeFqn("User"))
            .isEqualTo("User");
    }

    // ========== Short Name Extraction Tests ==========

    @Test
    public void testGetShortName_NestedNamespace() {
        assertThat(getShortName("App\\Models\\User"))
            .isEqualTo("User");
    }

    @Test
    public void testGetShortName_SingleNamespace() {
        assertThat(getShortName("App\\User"))
            .isEqualTo("User");
    }

    @Test
    public void testGetShortName_GlobalNamespace() {
        assertThat(getShortName("User"))
            .isEqualTo("User");
    }

    @Test
    public void testGetShortName_WithLeadingBackslash() {
        String normalized = normalizeFqn("\\App\\Models\\User");
        assertThat(getShortName(normalized))
            .isEqualTo("User");
    }

    // ========== Use Statement Generation Tests ==========

    @Test
    public void testGenerateUseStatement_Simple() {
        String fqn = "App\\Models\\User";
        String useStatement = "use " + normalizeFqn(fqn) + ";";
        assertThat(useStatement).isEqualTo("use App\\Models\\User;");
    }

    @Test
    public void testGenerateUseStatement_WithLeadingBackslash() {
        String fqn = "\\App\\Models\\User";
        String useStatement = "use " + normalizeFqn(fqn) + ";";
        assertThat(useStatement).isEqualTo("use App\\Models\\User;");
    }

    // ========== Duplicate Detection Tests ==========

    @Test
    public void testDuplicateDetection_ExactMatch() {
        String text = "<?php\nnamespace App;\nuse App\\Models\\User;\nclass Test {}";
        String fqn = "App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isTrue();
    }

    @Test
    public void testDuplicateDetection_WithLeadingBackslashInUse() {
        String text = "<?php\nnamespace App;\nuse \\App\\Models\\User;\nclass Test {}";
        String fqn = "App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isTrue();
    }

    @Test
    public void testDuplicateDetection_FqnHasLeadingBackslash() {
        String text = "<?php\nnamespace App;\nuse App\\Models\\User;\nclass Test {}";
        String fqn = "\\App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isTrue();
    }

    @Test
    public void testDuplicateDetection_BothHaveLeadingBackslash() {
        String text = "<?php\nnamespace App;\nuse \\App\\Models\\User;\nclass Test {}";
        String fqn = "\\App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isTrue();
    }

    @Test
    public void testDuplicateDetection_NotPresent() {
        String text = "<?php\nnamespace App;\nuse App\\Models\\Order;\nclass Test {}";
        String fqn = "App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isFalse();
    }

    @Test
    public void testDuplicateDetection_NoUseStatements() {
        String text = "<?php\nnamespace App;\nclass Test {}";
        String fqn = "App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isFalse();
    }

    @Test
    public void testDuplicateDetection_ExtraWhitespace() {
        String text = "<?php\nnamespace App;\nuse   App\\Models\\User  ;\nclass Test {}";
        String fqn = "App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isTrue();
    }

    @Test
    public void testDuplicateDetection_PartialMatch_ShouldNotMatch() {
        String text = "<?php\nnamespace App;\nuse App\\Models\\UserService;\nclass Test {}";
        String fqn = "App\\Models\\User";
        assertThat(hasUseStatement(text, fqn)).isFalse();
    }

    @Test
    public void testDuplicateDetection_GlobalClass() {
        String text = "<?php\nuse DateTime;\nclass Test {}";
        String fqn = "DateTime";
        assertThat(hasUseStatement(text, fqn)).isTrue();
    }

    // ========== Same Namespace Detection Tests ==========

    @Test
    public void testSameNamespace_BothInSameNamespace() {
        String classNamespace = "App\\Models";
        String fileNamespace = "App\\Models";
        assertThat(isSameNamespace(classNamespace, fileNamespace)).isTrue();
    }

    @Test
    public void testSameNamespace_DifferentNamespaces() {
        String classNamespace = "App\\Models";
        String fileNamespace = "App\\Services";
        assertThat(isSameNamespace(classNamespace, fileNamespace)).isFalse();
    }

    @Test
    public void testSameNamespace_ChildNamespace_NotSame() {
        String classNamespace = "App\\Models\\Entities";
        String fileNamespace = "App\\Models";
        assertThat(isSameNamespace(classNamespace, fileNamespace)).isFalse();
    }

    @Test
    public void testSameNamespace_BothGlobal() {
        String classNamespace = "";
        String fileNamespace = "";
        assertThat(isSameNamespace(classNamespace, fileNamespace)).isTrue();
    }

    // ========== Global Namespace Class Detection Tests ==========

    @Test
    public void testIsGlobalNamespaceClass_True() {
        assertThat(isGlobalNamespaceClass("DateTime")).isTrue();
        assertThat(isGlobalNamespaceClass("Exception")).isTrue();
        assertThat(isGlobalNamespaceClass("MyClass")).isTrue();
    }

    @Test
    public void testIsGlobalNamespaceClass_False() {
        assertThat(isGlobalNamespaceClass("App\\Models\\User")).isFalse();
        assertThat(isGlobalNamespaceClass("App\\User")).isFalse();
    }

    // ========== Reference Replacement Logic Tests ==========

    @Test
    public void testGetReplacement_SameNamespace() {
        String classFqn = "App\\Models\\User";
        String fileNamespace = "App\\Models";
        String replacement = getReplacement(classFqn, fileNamespace);
        assertThat(replacement).isEqualTo("User");
    }

    @Test
    public void testGetReplacement_DifferentNamespace_AddsUseStatement() {
        String classFqn = "App\\Models\\User";
        String fileNamespace = "App\\Services";
        String replacement = getReplacement(classFqn, fileNamespace);
        // Should use short name (use statement would be added separately)
        assertThat(replacement).isEqualTo("User");
    }

    @Test
    public void testGetReplacement_GlobalNamespaceClass_UsesBackslash() {
        String classFqn = "DateTime";
        String fileNamespace = "App\\Services";
        String replacement = getReplacement(classFqn, fileNamespace);
        // Global namespace classes should be prefixed with backslash
        assertThat(replacement).isEqualTo("\\DateTime");
    }

    @Test
    public void testGetReplacement_GlobalNamespaceClass_BothGlobal() {
        String classFqn = "MyHelper";
        String fileNamespace = "";
        String replacement = getReplacement(classFqn, fileNamespace);
        // Both in global namespace - just use short name
        assertThat(replacement).isEqualTo("MyHelper");
    }

    // ========== Use Statement Insertion Position Tests ==========

    @Test
    public void testFindInsertPosition_AfterExistingUseStatements() {
        String text = "<?php\nnamespace App;\nuse App\\Models\\Order;\nuse App\\Models\\Product;\n\nclass Test {}";
        int pos = findUseStatementInsertPosition(text);
        // Should be after the last use statement (at or after the semicolon)
        int lastUseEnd = text.indexOf("use App\\Models\\Product;") + "use App\\Models\\Product;".length();
        assertThat(pos).isGreaterThanOrEqualTo(lastUseEnd);
    }

    @Test
    public void testFindInsertPosition_AfterNamespace() {
        String text = "<?php\nnamespace App;\n\nclass Test {}";
        int pos = findUseStatementInsertPosition(text);
        // Should be after namespace declaration (at or after the semicolon)
        int nsEnd = text.indexOf("namespace App;") + "namespace App;".length();
        assertThat(pos).isGreaterThanOrEqualTo(nsEnd);
    }

    @Test
    public void testFindInsertPosition_AfterPhpTag() {
        String text = "<?php\n\nclass Test {}";
        int pos = findUseStatementInsertPosition(text);
        // Should be after <?php tag (position 5 or greater)
        assertThat(pos).isGreaterThanOrEqualTo(5);
    }

    // ========== Namespace Extraction Tests ==========

    @Test
    public void testExtractNamespace_FromFqn() {
        assertThat(extractNamespace("App\\Models\\User")).isEqualTo("App\\Models");
    }

    @Test
    public void testExtractNamespace_SingleLevel() {
        assertThat(extractNamespace("App\\User")).isEqualTo("App");
    }

    @Test
    public void testExtractNamespace_GlobalNamespace() {
        assertThat(extractNamespace("User")).isEqualTo("");
    }

    // ========== Helper Methods (mirrors ManualReferenceUpdater logic) ==========

    private String normalizeFqn(String fqn) {
        return fqn.startsWith("\\") ? fqn.substring(1) : fqn;
    }

    private String getShortName(String fqn) {
        int lastSlash = fqn.lastIndexOf('\\');
        return lastSlash >= 0 ? fqn.substring(lastSlash + 1) : fqn;
    }

    private String extractNamespace(String fqn) {
        if (fqn == null) return "";
        int lastSlash = fqn.lastIndexOf('\\');
        return lastSlash >= 0 ? fqn.substring(0, lastSlash) : "";
    }

    private boolean hasUseStatement(String text, String fqn) {
        String normalizedFqn = normalizeFqn(fqn);
        String escapedFqn = Pattern.quote(normalizedFqn);
        Pattern existingUsePattern = Pattern.compile(
            "use\\s+\\\\?" + escapedFqn + "\\s*;"
        );
        return existingUsePattern.matcher(text).find();
    }

    private boolean isSameNamespace(String classNamespace, String fileNamespace) {
        return classNamespace.equals(fileNamespace);
    }

    private boolean isGlobalNamespaceClass(String fqn) {
        String normalizedFqn = normalizeFqn(fqn);
        String namespace = extractNamespace(normalizedFqn);
        return namespace.isEmpty();
    }

    /**
     * Mirrors the logic in ManualReferenceUpdater.updateClassReference
     * Returns what the class reference should be replaced with.
     */
    private String getReplacement(String classFqn, String fileNamespace) {
        String normalizedFqn = normalizeFqn(classFqn);
        String shortName = getShortName(normalizedFqn);
        String classNamespace = extractNamespace(normalizedFqn);
        boolean isGlobalNsClass = classNamespace.isEmpty();
        boolean sameNamespace = classNamespace.equals(fileNamespace);

        if (sameNamespace) {
            // Same namespace - just use the short name
            return shortName;
        } else if (isGlobalNsClass) {
            // Global namespace class - use backslash prefix
            return "\\" + shortName;
        } else {
            // Different namespace - use short name (use statement added separately)
            return shortName;
        }
    }

    private int findUseStatementInsertPosition(String text) {
        // Check for existing use statements
        Pattern usePattern = Pattern.compile("(use\\s+[^;]+;\\s*)+");
        Matcher useMatcher = usePattern.matcher(text);

        if (useMatcher.find()) {
            return useMatcher.end();
        }

        // Check for namespace
        Pattern nsPattern = Pattern.compile("namespace\\s+[^;]+;\\s*");
        Matcher nsMatcher = nsPattern.matcher(text);

        if (nsMatcher.find()) {
            return nsMatcher.end();
        }

        // Fall back to after <?php tag
        Pattern phpTagPattern = Pattern.compile("<\\?php\\s*");
        Matcher phpMatcher = phpTagPattern.matcher(text);

        if (phpMatcher.find()) {
            return phpMatcher.end();
        }

        return 0;
    }

    // ========== Insert Use Statement Tests (mirrors insertUseStatement in ManualReferenceUpdater) ==========

    @Test
    public void testInsertUseStatement_AfterExistingUseStatements() {
        String text = "<?php\nnamespace App;\n\nuse App\\Models\\Order;\nuse App\\Models\\Product;\n\nclass Test {}";
        String result = insertUseStatement(text, "use App\\Models\\User;");

        // Should insert after the last use statement
        assertThat(result).contains("use App\\Models\\Product;\nuse App\\Models\\User;");
        // Verify order
        int productPos = result.indexOf("use App\\Models\\Product;");
        int userPos = result.indexOf("use App\\Models\\User;");
        assertThat(userPos).isGreaterThan(productPos);
    }

    @Test
    public void testInsertUseStatement_AfterNamespace_NoExistingUse() {
        String text = "<?php\nnamespace App;\n\nclass Test {}";
        String result = insertUseStatement(text, "use App\\Models\\User;");

        // Should insert after namespace
        assertThat(result).contains("namespace App;\n\nuse App\\Models\\User;");
        int nsPos = result.indexOf("namespace App;");
        int usePos = result.indexOf("use App\\Models\\User;");
        assertThat(usePos).isGreaterThan(nsPos);
    }

    @Test
    public void testInsertUseStatement_AfterPhpTag_NoNamespace() {
        String text = "<?php\n\nclass Test {}";
        String result = insertUseStatement(text, "use App\\Models\\User;");

        // Should insert after <?php tag, before class
        int phpPos = result.indexOf("<?php");
        int usePos = result.indexOf("use App\\Models\\User;");
        int classPos = result.indexOf("class Test");
        assertThat(usePos).isGreaterThan(phpPos);
        assertThat(classPos).isGreaterThan(usePos);
        // Use statement should be near the top, not at the end
        assertThat(usePos).isLessThan(result.length() / 2);
    }

    @Test
    public void testInsertUseStatement_NotAtEndOfFile() {
        String text = "<?php\n\nclass Test {\n    public function foo() {}\n}";
        String result = insertUseStatement(text, "use App\\Models\\User;");

        // Use statement should NOT be at the end
        assertThat(result).doesNotEndWith("use App\\Models\\User;");
        assertThat(result).endsWith("}");

        // Should be before class definition
        int usePos = result.indexOf("use App\\Models\\User;");
        int classPos = result.indexOf("class Test");
        assertThat(usePos).isLessThan(classPos);
    }

    @Test
    public void testInsertUseStatement_MultipleInsertions_CorrectOrder() {
        String text = "<?php\nnamespace App;\n\nclass Test {}";

        // Insert first use statement
        String result = insertUseStatement(text, "use App\\Models\\User;");
        // Insert second use statement
        result = insertUseStatement(result, "use App\\Models\\Order;");

        // Both should be in correct position (after namespace, before class)
        int nsPos = result.indexOf("namespace App;");
        int userPos = result.indexOf("use App\\Models\\User;");
        int orderPos = result.indexOf("use App\\Models\\Order;");
        int classPos = result.indexOf("class Test");

        assertThat(userPos).isGreaterThan(nsPos);
        assertThat(orderPos).isGreaterThan(nsPos);
        assertThat(classPos).isGreaterThan(userPos);
        assertThat(classPos).isGreaterThan(orderPos);
    }

    // ========== Global Namespace Prefixing Tests ==========

    @Test
    public void testPrefixGlobalClass_DateTime() {
        String text = "<?php\nnamespace App;\n\nclass Test {\n    public function foo(): DateTime {}\n}";
        String result = prefixGlobalClasses(text, java.util.List.of("DateTime"));

        assertThat(result).contains("\\DateTime");
        assertThat(result).doesNotContain(": DateTime");
    }

    @Test
    public void testPrefixGlobalClass_Exception() {
        String text = "<?php\nnamespace App;\n\nclass Test {\n    public function foo() { throw new Exception(); }\n}";
        String result = prefixGlobalClasses(text, java.util.List.of("Exception"));

        assertThat(result).contains("new \\Exception()");
    }

    @Test
    public void testPrefixGlobalClass_MultipleOccurrences() {
        String text = "<?php\nnamespace App;\n\nclass Test {\n    public DateTime $date;\n    public function foo(): DateTime { return new DateTime(); }\n}";
        String result = prefixGlobalClasses(text, java.util.List.of("DateTime"));

        // Count occurrences of \DateTime
        int count = countOccurrences(result, "\\DateTime");
        assertThat(count).isEqualTo(3); // All three should be prefixed
    }

    @Test
    public void testPrefixGlobalClass_DoNotPrefixAlreadyQualified() {
        String text = "<?php\nnamespace App;\n\nclass Test {\n    public function foo(): \\DateTime {}\n}";
        String result = prefixGlobalClasses(text, java.util.List.of("DateTime"));

        // Should NOT become \\DateTime
        assertThat(result).doesNotContain("\\\\DateTime");
        assertThat(result).contains("\\DateTime");
    }

    @Test
    public void testPrefixGlobalClass_DoNotPrefixInStrings() {
        String text = "<?php\nnamespace App;\n\nclass Test {\n    public function foo() { return 'DateTime is a class'; }\n}";
        String result = prefixGlobalClasses(text, java.util.List.of("DateTime"));

        // Should not prefix inside strings
        assertThat(result).contains("'DateTime is a class'");
    }

    @Test
    public void testPrefixGlobalClass_DoNotPrefixPartOfOtherName() {
        String text = "<?php\nnamespace App;\n\nclass Test {\n    public function foo(): DateTimeImmutable {}\n}";
        String result = prefixGlobalClasses(text, java.util.List.of("DateTime"));

        // Should NOT prefix DateTime when it's part of DateTimeImmutable
        assertThat(result).contains("DateTimeImmutable");
        assertThat(result).doesNotContain("\\DateTimeImmutable");
    }

    @Test
    public void testPrefixGlobalClass_CustomClass() {
        String text = "<?php\nnamespace Entities;\n\nclass Order {\n    private Cart $cart;\n    public function setCart(Cart $cart) { $this->cart = $cart; }\n}";
        String result = prefixGlobalClasses(text, java.util.List.of("Cart"));

        // Cart should be prefixed
        assertThat(result).contains("\\Cart");
        int count = countOccurrences(result, "\\Cart");
        assertThat(count).isEqualTo(2); // Both occurrences should be prefixed
    }

    // ========== Complete Scenario Tests ==========

    @Test
    public void testCompleteScenario_MoveFromGlobalToNamespace() {
        // Simulating moving a class from global namespace to App\Entities
        String originalCode = "<?php\n\nclass Order {\n    private Cart $cart;\n    private DateTime $created;\n    \n    public function __construct(Cart $cart) {\n        $this->cart = $cart;\n        $this->created = new DateTime();\n    }\n}";

        // After moving to App\Entities namespace, global classes should be prefixed
        java.util.List<String> globalClasses = java.util.List.of("Cart", "DateTime");
        String result = prefixGlobalClasses(originalCode, globalClasses);

        // Add namespace declaration
        result = result.replace("<?php\n\n", "<?php\n\nnamespace App\\Entities;\n\n");

        assertThat(result).contains("namespace App\\Entities;");
        assertThat(result).contains("\\Cart");
        assertThat(result).contains("\\DateTime");
        assertThat(result).contains("new \\DateTime()");
    }

    @Test
    public void testCompleteScenario_AddUseStatementForNamespacedClass() {
        String code = "<?php\nnamespace App\\Services;\n\nclass OrderService {\n}";

        // Add use statement for App\Models\User
        if (!hasUseStatement(code, "App\\Models\\User")) {
            code = insertUseStatement(code, "use App\\Models\\User;");
        }

        assertThat(code).contains("use App\\Models\\User;");

        // Try adding again - should not duplicate
        if (!hasUseStatement(code, "App\\Models\\User")) {
            code = insertUseStatement(code, "use App\\Models\\User;");
        }

        // Count use statements - should only be one
        int count = countOccurrences(code, "use App\\Models\\User;");
        assertThat(count).isEqualTo(1);
    }

    // ========== Additional Helper Methods ==========

    /**
     * Mirrors the insertUseStatement method in ManualReferenceUpdater
     */
    private String insertUseStatement(String text, String useStatement) {
        // Check for existing use statements - find the LAST one
        Pattern usePattern = Pattern.compile("use\\s+[^;]+;");
        Matcher useMatcher = usePattern.matcher(text);

        int lastUseEnd = -1;
        while (useMatcher.find()) {
            lastUseEnd = useMatcher.end();
        }

        if (lastUseEnd > 0) {
            // Insert after the last use statement
            return text.substring(0, lastUseEnd) + "\n" + useStatement + text.substring(lastUseEnd);
        }

        // No existing use statements - check for namespace
        Pattern nsPattern = Pattern.compile("namespace\\s+[^;]+;");
        Matcher nsMatcher = nsPattern.matcher(text);

        if (nsMatcher.find()) {
            int insertPos = nsMatcher.end();
            return text.substring(0, insertPos) + "\n\n" + useStatement + text.substring(insertPos);
        }

        // No namespace - insert after <?php tag
        Pattern phpTagPattern = Pattern.compile("<\\?php\\s*");
        Matcher phpMatcher = phpTagPattern.matcher(text);

        if (phpMatcher.find()) {
            int insertPos = phpMatcher.end();
            return text.substring(0, insertPos) + "\n" + useStatement + "\n" + text.substring(insertPos);
        }

        // Fallback - prepend
        return useStatement + "\n" + text;
    }

    /**
     * Mirrors the global class prefixing logic in ManualReferenceUpdater.updateInternalReferences
     */
    private String prefixGlobalClasses(String text, java.util.List<String> classNames) {
        String result = text;
        for (String className : classNames) {
            // Match class name that is NOT preceded by \ or another identifier char
            // and NOT followed by \ (which would make it a namespace prefix)
            String pattern = "(?<![\\\\A-Za-z0-9_])" + Pattern.quote(className) + "(?![\\\\A-Za-z0-9_])";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(result);

            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                // Check if this is in a string literal (basic check)
                int pos = m.start();
                String before = result.substring(0, pos);

                long singleQuotes = before.chars().filter(c -> c == '\'').count();
                long doubleQuotes = before.chars().filter(c -> c == '"').count();
                boolean inString = (singleQuotes % 2 == 1) || (doubleQuotes % 2 == 1);

                if (!inString) {
                    m.appendReplacement(sb, "\\\\" + className);
                } else {
                    m.appendReplacement(sb, className);
                }
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
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
}
