package com.github.psiMcpServer.php;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for internal reference updates when moving PHP classes.
 * When a class is moved to a new namespace, references inside that class
 * to other classes in the old namespace need to be updated.
 */
public class InternalReferenceUpdateTest {

    // ========== Class Reference Detection Tests ==========

    @Test
    public void testDetectClassReferences_TypeHints() {
        String code = """
            <?php
            namespace App\\Services;

            class UserService {
                public function getUser(): User {}
                public function setHelper(Helper $helper) {}
            }
            """;

        List<String> references = findUnqualifiedClassReferences(code, List.of("User", "Helper", "Order"));

        assertThat(references).containsExactlyInAnyOrder("User", "Helper");
    }

    @Test
    public void testDetectClassReferences_NewInstances() {
        String code = """
            <?php
            namespace App\\Services;

            class UserService {
                public function create() {
                    return new User();
                }
            }
            """;

        List<String> references = findUnqualifiedClassReferences(code, List.of("User"));

        assertThat(references).contains("User");
    }

    @Test
    public void testDetectClassReferences_PropertyTypes() {
        String code = """
            <?php
            namespace App\\Services;

            class UserService {
                private User $user;
                private Helper $helper;
            }
            """;

        List<String> references = findUnqualifiedClassReferences(code, List.of("User", "Helper"));

        assertThat(references).containsExactlyInAnyOrder("User", "Helper");
    }

    @Test
    public void testDetectClassReferences_ExtendsImplements() {
        String code = """
            <?php
            namespace App\\Services;

            class UserService extends BaseService implements ServiceInterface {
            }
            """;

        List<String> references = findUnqualifiedClassReferences(code, List.of("BaseService", "ServiceInterface"));

        assertThat(references).containsExactlyInAnyOrder("BaseService", "ServiceInterface");
    }

    @Test
    public void testDetectClassReferences_IgnoresQualified() {
        String code = """
            <?php
            namespace App\\Services;

            class UserService {
                public function getUser(): \\App\\Models\\User {}
            }
            """;

        // Should NOT detect User because it's fully qualified
        List<String> references = findUnqualifiedClassReferences(code, List.of("User"));

        assertThat(references).isEmpty();
    }

    @Test
    public void testDetectClassReferences_IgnoresStrings() {
        String code = """
            <?php
            namespace App\\Services;

            class UserService {
                public function getName() {
                    return 'User is the class name';
                }
            }
            """;

        // Should NOT detect User because it's in a string
        List<String> references = findUnqualifiedClassReferences(code, List.of("User"));

        assertThat(references).isEmpty();
    }

    // ========== Use Statement Addition Tests ==========

    @Test
    public void testAddUseStatements_ForSiblingClasses() {
        String oldNamespace = "App\\Services";
        String newNamespace = "App\\Domain\\Services";
        List<String> siblingClasses = List.of("Helper", "Validator");

        List<String> useStatements = generateUseStatements(siblingClasses, oldNamespace);

        assertThat(useStatements).containsExactlyInAnyOrder(
            "use App\\Services\\Helper;",
            "use App\\Services\\Validator;"
        );
    }

    @Test
    public void testAddUseStatements_EmptyList() {
        List<String> siblingClasses = List.of();
        String oldNamespace = "App\\Services";

        List<String> useStatements = generateUseStatements(siblingClasses, oldNamespace);

        assertThat(useStatements).isEmpty();
    }

    @Test
    public void testAddUseStatements_GlobalNamespace() {
        String oldNamespace = "";
        List<String> siblingClasses = List.of("Helper");

        List<String> useStatements = generateUseStatements(siblingClasses, oldNamespace);

        // From global namespace, no use statement needed (will use backslash prefix instead)
        assertThat(useStatements).isEmpty();
    }

    // ========== Global Namespace Reference Prefixing Tests ==========

    @Test
    public void testPrefixGlobalReferences_SimpleCase() {
        String code = """
            <?php
            class Order {
                private Cart $cart;
            }
            """;

        String result = prefixGlobalNamespaceReferences(code, List.of("Cart"), "App\\Entities");

        assertThat(result).contains("\\Cart");
    }

    @Test
    public void testPrefixGlobalReferences_MultipleReferences() {
        String code = """
            <?php
            class Order {
                private Cart $cart;
                private DateTime $created;

                public function __construct(Cart $cart) {
                    $this->cart = $cart;
                    $this->created = new DateTime();
                }
            }
            """;

        String result = prefixGlobalNamespaceReferences(code, List.of("Cart", "DateTime"), "App\\Entities");

        assertThat(countOccurrences(result, "\\Cart")).isEqualTo(2);
        assertThat(countOccurrences(result, "\\DateTime")).isEqualTo(2);
    }

    @Test
    public void testPrefixGlobalReferences_DoNotDoublePrefix() {
        String code = """
            <?php
            class Order {
                private \\Cart $cart;
            }
            """;

        String result = prefixGlobalNamespaceReferences(code, List.of("Cart"), "App\\Entities");

        // Should NOT become \\Cart
        assertThat(result).doesNotContain("\\\\Cart");
        assertThat(result).contains("\\Cart");
    }

    @Test
    public void testPrefixGlobalReferences_PreservesNamespacedReferences() {
        String code = """
            <?php
            class Order {
                private App\\Models\\Cart $cart;
            }
            """;

        String result = prefixGlobalNamespaceReferences(code, List.of("Cart"), "App\\Entities");

        // Should NOT prefix namespaced reference
        assertThat(result).contains("App\\Models\\Cart");
        assertThat(result).doesNotContain("App\\Models\\\\Cart");
    }

    @Test
    public void testPrefixGlobalReferences_TargetIsGlobalNamespace() {
        String code = """
            <?php
            class Order {
                private Cart $cart;
            }
            """;

        // Moving to global namespace - no prefix needed
        String result = prefixGlobalNamespaceReferences(code, List.of("Cart"), "");

        assertThat(result).doesNotContain("\\Cart");
        assertThat(result).contains("Cart");
    }

    // ========== Require/Include Path Update Tests ==========

    @Test
    public void testUpdateRequirePath_MovingDeeper() {
        String code = "<?php\nrequire_once __DIR__ . '../Models/User.php';\n";

        // Moving 1 level deeper (e.g., from App/ to App/Services/)
        String result = updateRequirePaths(code, 1);

        assertThat(result).contains("__DIR__ . '../../Models/User.php'");
    }

    @Test
    public void testUpdateRequirePath_MovingShallower() {
        String code = "<?php\nrequire_once __DIR__ . '../../Models/User.php';\n";

        // Moving 1 level shallower (e.g., from App/Services/ to App/)
        String result = updateRequirePaths(code, -1);

        assertThat(result).contains("__DIR__ . '../Models/User.php'");
    }

    @Test
    public void testUpdateRequirePath_SameLevel() {
        String code = "<?php\nrequire_once __DIR__ . '../Models/User.php';\n";

        // Not changing depth
        String result = updateRequirePaths(code, 0);

        assertThat(result).isEqualTo(code);
    }

    @Test
    public void testUpdateRequirePath_AbsolutePath_Unchanged() {
        String code = "<?php\nrequire_once '/var/www/vendor/autoload.php';\n";

        String result = updateRequirePaths(code, 2);

        // Absolute paths should NOT be changed
        assertThat(result).contains("'/var/www/vendor/autoload.php'");
    }

    @Test
    public void testUpdateRequirePath_DirnameStyle() {
        String code = "<?php\nrequire dirname(__FILE__) . '../config.php';\n";

        String result = updateRequirePaths(code, 1);

        assertThat(result).contains("dirname(__FILE__) . '../../config.php'");
    }

    // ========== Complete Scenario Tests ==========

    @Test
    public void testCompleteScenario_MovingClassWithSiblingReferences() {
        String originalCode = "<?php\nnamespace App\\Services;\n\nclass UserService {\n    private Helper $helper;\n}";

        String oldNamespace = "App\\Services";
        String newNamespace = "App\\Domain\\Services";

        // 1. Generate use statements for sibling classes
        List<String> siblingClasses = List.of("Helper", "Validator");
        List<String> useStatements = generateUseStatements(siblingClasses, oldNamespace);

        // 2. Update namespace in code
        String result = updateNamespace(originalCode, newNamespace);

        // 3. Add use statements
        for (String useStatement : useStatements) {
            result = insertUseStatement(result, useStatement);
        }

        assertThat(result).contains("namespace App\\Domain\\Services;");
        assertThat(result).contains("use App\\Services\\Helper;");
        assertThat(result).contains("use App\\Services\\Validator;");
    }

    @Test
    public void testCompleteScenario_MovingFromGlobalToNamespace() {
        String originalCode = "<?php\n\nclass Order {\n    private Cart $cart;\n    public function __construct() {\n        $this->created = new DateTime();\n    }\n}";

        String newNamespace = "App\\Entities";
        List<String> globalClasses = List.of("Cart", "DateTime");

        // 1. Add namespace
        String result = addNamespace(originalCode, newNamespace);

        // 2. Prefix global class references
        result = prefixGlobalNamespaceReferences(result, globalClasses, newNamespace);

        assertThat(result).contains("namespace App\\Entities;");
        assertThat(result).contains("\\Cart");
        assertThat(result).contains("\\DateTime");
        assertThat(result).contains("new \\DateTime()");
    }

    // ========== Helper Methods (mirror ManualReferenceUpdater logic) ==========

    private List<String> findUnqualifiedClassReferences(String code, List<String> classNames) {
        List<String> found = new ArrayList<>();

        for (String className : classNames) {
            // Match class name that is NOT preceded by \ (not qualified)
            // and IS followed by typical class usage patterns
            String pattern = "(?<![\\\\A-Za-z0-9_])" + Pattern.quote(className) + "(?![\\\\A-Za-z0-9_])";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(code);

            while (m.find()) {
                int pos = m.start();
                String before = code.substring(0, pos);

                // Skip if in string
                long singleQuotes = before.chars().filter(c -> c == '\'').count();
                long doubleQuotes = before.chars().filter(c -> c == '"').count();
                if (singleQuotes % 2 == 1 || doubleQuotes % 2 == 1) {
                    continue;
                }

                // Skip if it's a fully qualified name (preceded by backslash)
                if (pos > 0 && code.charAt(pos - 1) == '\\') {
                    continue;
                }

                if (!found.contains(className)) {
                    found.add(className);
                }
            }
        }

        return found;
    }

    private List<String> generateUseStatements(List<String> classNames, String oldNamespace) {
        List<String> statements = new ArrayList<>();

        if (oldNamespace == null || oldNamespace.isEmpty()) {
            // From global namespace - no use statements (will prefix with backslash instead)
            return statements;
        }

        for (String className : classNames) {
            statements.add("use " + oldNamespace + "\\" + className + ";");
        }

        return statements;
    }

    private String prefixGlobalNamespaceReferences(String code, List<String> classNames, String targetNamespace) {
        if (targetNamespace == null || targetNamespace.isEmpty()) {
            // Moving to global namespace - no prefix needed
            return code;
        }

        String result = code;
        for (String className : classNames) {
            // Match class name not preceded by backslash or other identifier chars
            String pattern = "(?<![\\\\A-Za-z0-9_])" + Pattern.quote(className) + "(?![\\\\A-Za-z0-9_])";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(result);

            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int pos = m.start();
                String before = result.substring(0, pos);

                // Skip if in string
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

    private String updateRequirePaths(String code, int depthDiff) {
        if (depthDiff == 0) {
            return code;
        }

        String result = code;

        // Pattern for relative paths in require/include statements
        Pattern pattern = Pattern.compile(
            "(require_once|require|include_once|include)\\s+" +
            "(__DIR__|dirname\\(__FILE__\\))\\s*\\.\\s*'(\\.\\.?/[^']+)'");

        Matcher m = pattern.matcher(result);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String keyword = m.group(1);
            String dirRef = m.group(2);
            String path = m.group(3);

            String newPath = adjustRelativePath(path, depthDiff);
            m.appendReplacement(sb, keyword + " " + dirRef + " . '" + newPath + "'");
        }
        m.appendTail(sb);

        return sb.toString();
    }

    private String adjustRelativePath(String path, int depthDiff) {
        if (depthDiff > 0) {
            // Moving deeper - add "../" prefixes at the start
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < depthDiff; i++) {
                prefix.append("../");
            }
            // If path starts with "./" or "../", insert prefix after removing leading "./"
            if (path.startsWith("./")) {
                return "./" + prefix + path.substring(2);
            } else if (path.startsWith("../")) {
                return prefix + path;
            } else if (path.startsWith("/")) {
                return "/" + prefix + path.substring(1);
            }
            return prefix + path;
        } else if (depthDiff < 0) {
            // Moving shallower - remove "../" prefixes
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

    private String updateNamespace(String code, String newNamespace) {
        return code.replaceFirst(
            "namespace\\s+[^;]+;",
            Matcher.quoteReplacement("namespace " + newNamespace + ";")
        );
    }

    private String addNamespace(String code, String namespace) {
        return code.replaceFirst(
            "<\\?php\\s*",
            Matcher.quoteReplacement("<?php\n\nnamespace " + namespace + ";\n\n")
        );
    }

    private String insertUseStatement(String code, String useStatement) {
        // Find last use statement
        Pattern usePattern = Pattern.compile("use\\s+[^;]+;");
        Matcher useMatcher = usePattern.matcher(code);

        int lastUseEnd = -1;
        while (useMatcher.find()) {
            lastUseEnd = useMatcher.end();
        }

        if (lastUseEnd > 0) {
            return code.substring(0, lastUseEnd) + "\n" + useStatement + code.substring(lastUseEnd);
        }

        // No existing use statements - insert after namespace
        Pattern nsPattern = Pattern.compile("namespace\\s+[^;]+;");
        Matcher nsMatcher = nsPattern.matcher(code);

        if (nsMatcher.find()) {
            int insertPos = nsMatcher.end();
            return code.substring(0, insertPos) + "\n\n" + useStatement + code.substring(insertPos);
        }

        return code;
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
