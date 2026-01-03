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
}
