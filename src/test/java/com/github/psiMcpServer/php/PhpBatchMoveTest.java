package com.github.psiMcpServer.php;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PHP batch move operations.
 * Tests glob pattern matching, directory structure preservation,
 * and namespace calculation logic used in PhpBatchMoveHandler.
 */
public class PhpBatchMoveTest {

    // ========== Glob Pattern to Regex Conversion Tests ==========

    @Test
    public void testGlobToRegex_SingleAsterisk() {
        Pattern pattern = globToRegex("*.php");
        assertThat(pattern.matcher("UserService.php").matches()).isTrue();
        assertThat(pattern.matcher("Test.php").matches()).isTrue();
        assertThat(pattern.matcher("a.php").matches()).isTrue();
        assertThat(pattern.matcher(".php").matches()).isTrue();
        assertThat(pattern.matcher("UserService.txt").matches()).isFalse();
    }

    @Test
    public void testGlobToRegex_PrefixPattern() {
        Pattern pattern = globToRegex("*Controller.php");
        assertThat(pattern.matcher("UserController.php").matches()).isTrue();
        assertThat(pattern.matcher("OrderController.php").matches()).isTrue();
        assertThat(pattern.matcher("Controller.php").matches()).isTrue();
        assertThat(pattern.matcher("UserService.php").matches()).isFalse();
        assertThat(pattern.matcher("UserControllerTest.php").matches()).isFalse();
    }

    @Test
    public void testGlobToRegex_SuffixPattern() {
        Pattern pattern = globToRegex("Service*.php");
        assertThat(pattern.matcher("ServiceUser.php").matches()).isTrue();
        assertThat(pattern.matcher("ServiceOrder.php").matches()).isTrue();
        assertThat(pattern.matcher("Service.php").matches()).isTrue();
        assertThat(pattern.matcher("UserService.php").matches()).isFalse();
    }

    @Test
    public void testGlobToRegex_MiddleAsterisk() {
        Pattern pattern = globToRegex("User*Service.php");
        assertThat(pattern.matcher("UserService.php").matches()).isTrue();
        assertThat(pattern.matcher("UserOrderService.php").matches()).isTrue();
        assertThat(pattern.matcher("UserAuthenticationService.php").matches()).isTrue();
        assertThat(pattern.matcher("UserController.php").matches()).isFalse();
    }

    @Test
    public void testGlobToRegex_QuestionMark() {
        Pattern pattern = globToRegex("User?.php");
        assertThat(pattern.matcher("User1.php").matches()).isTrue();
        assertThat(pattern.matcher("UserA.php").matches()).isTrue();
        assertThat(pattern.matcher("User.php").matches()).isFalse();
        assertThat(pattern.matcher("User12.php").matches()).isFalse();
    }

    @Test
    public void testGlobToRegex_MultipleQuestionMarks() {
        Pattern pattern = globToRegex("Test??.php");
        assertThat(pattern.matcher("Test01.php").matches()).isTrue();
        assertThat(pattern.matcher("TestAB.php").matches()).isTrue();
        assertThat(pattern.matcher("Test1.php").matches()).isFalse();
        assertThat(pattern.matcher("Test123.php").matches()).isFalse();
    }

    @Test
    public void testGlobToRegex_EscapesDot() {
        Pattern pattern = globToRegex("file.name.php");
        assertThat(pattern.matcher("file.name.php").matches()).isTrue();
        assertThat(pattern.matcher("fileXnameXphp").matches()).isFalse();
    }

    @Test
    public void testGlobToRegex_CaseInsensitive() {
        Pattern pattern = globToRegex("*Controller.php");
        assertThat(pattern.matcher("usercontroller.php").matches()).isTrue();
        assertThat(pattern.matcher("USERCONTROLLER.PHP").matches()).isTrue();
    }

    @Test
    public void testGlobToRegex_MultipleAsterisks() {
        Pattern pattern = globToRegex("*Service*Test.php");
        assertThat(pattern.matcher("UserServiceTest.php").matches()).isTrue();
        assertThat(pattern.matcher("ServiceTest.php").matches()).isTrue();
        assertThat(pattern.matcher("OrderServiceUnitTest.php").matches()).isTrue();
    }

    // ========== File Pattern Matching Tests ==========

    @Test
    public void testMatchFiles_AllPhpFiles() {
        List<String> files = List.of(
            "UserController.php",
            "OrderController.php",
            "UserService.php",
            "README.md"
        );

        List<String> matched = matchFiles(files, "*.php");

        assertThat(matched).containsExactlyInAnyOrder(
            "UserController.php",
            "OrderController.php",
            "UserService.php"
        );
    }

    @Test
    public void testMatchFiles_ControllersOnly() {
        List<String> files = List.of(
            "UserController.php",
            "OrderController.php",
            "UserService.php",
            "UserRepository.php"
        );

        List<String> matched = matchFiles(files, "*Controller.php");

        assertThat(matched).containsExactlyInAnyOrder(
            "UserController.php",
            "OrderController.php"
        );
    }

    @Test
    public void testMatchFiles_NoMatches() {
        List<String> files = List.of(
            "UserController.php",
            "OrderController.php"
        );

        List<String> matched = matchFiles(files, "*Repository.php");

        assertThat(matched).isEmpty();
    }

    // ========== Relative Path Calculation Tests ==========

    @Test
    public void testGetRelativePath_DirectChild() {
        String parent = "/project/src";
        String child = "/project/src/Controllers";

        String relative = getRelativePath(parent, child);

        assertThat(relative).isEqualTo("Controllers");
    }

    @Test
    public void testGetRelativePath_NestedChild() {
        String parent = "/project/src";
        String child = "/project/src/Http/Controllers/Api";

        String relative = getRelativePath(parent, child);

        assertThat(relative).isEqualTo("Http/Controllers/Api");
    }

    @Test
    public void testGetRelativePath_SameDirectory() {
        String parent = "/project/src";
        String child = "/project/src";

        String relative = getRelativePath(parent, child);

        assertThat(relative).isEqualTo("");
    }

    @Test
    public void testGetRelativePath_NotAChild() {
        String parent = "/project/src";
        String child = "/project/tests";

        String relative = getRelativePath(parent, child);

        assertThat(relative).isNull();
    }

    // ========== Namespace Calculation Tests ==========

    @Test
    public void testCalculateNamespace_PreserveStructure() {
        String baseNamespace = "App\\Http\\Controllers";
        String relativePath = "Api/V2";

        String namespace = calculateNamespace(baseNamespace, relativePath, true);

        assertThat(namespace).isEqualTo("App\\Http\\Controllers\\Api\\V2");
    }

    @Test
    public void testCalculateNamespace_NoRelativePath() {
        String baseNamespace = "App\\Http\\Controllers";
        String relativePath = "";

        String namespace = calculateNamespace(baseNamespace, relativePath, true);

        assertThat(namespace).isEqualTo("App\\Http\\Controllers");
    }

    @Test
    public void testCalculateNamespace_DoNotPreserveStructure() {
        String baseNamespace = "App\\Http\\Controllers";
        String relativePath = "Api/V2";

        String namespace = calculateNamespace(baseNamespace, relativePath, false);

        assertThat(namespace).isEqualTo("App\\Http\\Controllers");
    }

    @Test
    public void testCalculateNamespace_NullRelativePath() {
        String baseNamespace = "App\\Services";
        String relativePath = null;

        String namespace = calculateNamespace(baseNamespace, relativePath, true);

        assertThat(namespace).isEqualTo("App\\Services");
    }

    @Test
    public void testCalculateNamespace_EmptyBaseNamespace() {
        String baseNamespace = "";
        String relativePath = "Controllers";

        String namespace = calculateNamespace(baseNamespace, relativePath, true);

        assertThat(namespace).isEqualTo("Controllers");
    }

    // ========== Batch Result Aggregation Tests ==========

    @Test
    public void testBatchResultAggregation_AllSuccess() {
        List<FileMoveResult> results = List.of(
            new FileMoveResult("file1.php", true, 5),
            new FileMoveResult("file2.php", true, 3),
            new FileMoveResult("file3.php", true, 2)
        );

        BatchSummary summary = aggregateResults(results);

        assertThat(summary.totalFiles).isEqualTo(3);
        assertThat(summary.movedFiles).isEqualTo(3);
        assertThat(summary.failedFiles).isEqualTo(0);
        assertThat(summary.totalReferencesUpdated).isEqualTo(10);
        assertThat(summary.isSuccess).isTrue();
    }

    @Test
    public void testBatchResultAggregation_PartialSuccess() {
        List<FileMoveResult> results = List.of(
            new FileMoveResult("file1.php", true, 5),
            new FileMoveResult("file2.php", false, 0),
            new FileMoveResult("file3.php", true, 2)
        );

        BatchSummary summary = aggregateResults(results);

        assertThat(summary.totalFiles).isEqualTo(3);
        assertThat(summary.movedFiles).isEqualTo(2);
        assertThat(summary.failedFiles).isEqualTo(1);
        assertThat(summary.totalReferencesUpdated).isEqualTo(7);
        assertThat(summary.isSuccess).isTrue(); // Still considered success if any moved
    }

    @Test
    public void testBatchResultAggregation_AllFailed() {
        List<FileMoveResult> results = List.of(
            new FileMoveResult("file1.php", false, 0),
            new FileMoveResult("file2.php", false, 0)
        );

        BatchSummary summary = aggregateResults(results);

        assertThat(summary.totalFiles).isEqualTo(2);
        assertThat(summary.movedFiles).isEqualTo(0);
        assertThat(summary.failedFiles).isEqualTo(2);
        assertThat(summary.isSuccess).isFalse();
    }

    @Test
    public void testBatchResultAggregation_EmptyList() {
        List<FileMoveResult> results = List.of();

        BatchSummary summary = aggregateResults(results);

        assertThat(summary.totalFiles).isEqualTo(0);
        assertThat(summary.movedFiles).isEqualTo(0);
        assertThat(summary.failedFiles).isEqualTo(0);
        assertThat(summary.isSuccess).isFalse();
    }

    // ========== Directory Structure Preservation Tests ==========

    @Test
    public void testPreserveStructure_SingleLevel() {
        String sourceDir = "/project/src/Services";
        String targetDir = "/project/src/Domain/Services";
        String fileDir = "/project/src/Services/Payment";

        String targetPath = calculateTargetPath(sourceDir, targetDir, fileDir, true);

        assertThat(targetPath).isEqualTo("/project/src/Domain/Services/Payment");
    }

    @Test
    public void testPreserveStructure_NestedLevels() {
        String sourceDir = "/project/src/Services";
        String targetDir = "/project/src/Domain";
        String fileDir = "/project/src/Services/Payment/Gateway";

        String targetPath = calculateTargetPath(sourceDir, targetDir, fileDir, true);

        assertThat(targetPath).isEqualTo("/project/src/Domain/Payment/Gateway");
    }

    @Test
    public void testPreserveStructure_Disabled() {
        String sourceDir = "/project/src/Services";
        String targetDir = "/project/src/Domain";
        String fileDir = "/project/src/Services/Payment/Gateway";

        String targetPath = calculateTargetPath(sourceDir, targetDir, fileDir, false);

        assertThat(targetPath).isEqualTo("/project/src/Domain");
    }

    @Test
    public void testPreserveStructure_SameAsSource() {
        String sourceDir = "/project/src/Services";
        String targetDir = "/project/src/Domain";
        String fileDir = "/project/src/Services";

        String targetPath = calculateTargetPath(sourceDir, targetDir, fileDir, true);

        assertThat(targetPath).isEqualTo("/project/src/Domain");
    }

    // ========== Helper Methods (mirror PhpBatchMoveHandler logic) ==========

    private Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '\\' -> regex.append("\\\\");
                default -> regex.append(c);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private List<String> matchFiles(List<String> files, String globPattern) {
        Pattern pattern = globToRegex(globPattern);
        List<String> matched = new ArrayList<>();
        for (String file : files) {
            if (pattern.matcher(file).matches()) {
                matched.add(file);
            }
        }
        return matched;
    }

    private String getRelativePath(String parentPath, String childPath) {
        if (!childPath.startsWith(parentPath)) {
            return null;
        }

        String relative = childPath.substring(parentPath.length());
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative;
    }

    private String calculateNamespace(String baseNamespace, String relativePath, boolean preserveStructure) {
        if (!preserveStructure || relativePath == null || relativePath.isEmpty()) {
            return baseNamespace;
        }

        String namespaceAddition = relativePath.replace("/", "\\");
        if (baseNamespace.isEmpty()) {
            return namespaceAddition;
        }
        return baseNamespace + "\\" + namespaceAddition;
    }

    private String calculateTargetPath(String sourceDir, String targetDir, String fileDir, boolean preserveStructure) {
        if (!preserveStructure) {
            return targetDir;
        }

        String relativePath = getRelativePath(sourceDir, fileDir);
        if (relativePath == null || relativePath.isEmpty()) {
            return targetDir;
        }

        return targetDir + "/" + relativePath;
    }

    private BatchSummary aggregateResults(List<FileMoveResult> results) {
        int movedFiles = 0;
        int failedFiles = 0;
        int totalReferences = 0;

        for (FileMoveResult result : results) {
            if (result.success) {
                movedFiles++;
                totalReferences += result.referencesUpdated;
            } else {
                failedFiles++;
            }
        }

        return new BatchSummary(
            results.size(),
            movedFiles,
            failedFiles,
            totalReferences,
            movedFiles > 0
        );
    }

    // ========== Helper Records ==========

    record FileMoveResult(String fileName, boolean success, int referencesUpdated) {}

    record BatchSummary(
        int totalFiles,
        int movedFiles,
        int failedFiles,
        int totalReferencesUpdated,
        boolean isSuccess
    ) {}
}
