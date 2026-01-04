package com.github.psiMcpServer.php;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiDocumentManager;

/**
 * Handles manual reference updates for PHP class moves.
 * Only available when the PHP plugin is present (PHPStorm).
 */
public class ManualReferenceUpdater {

    private final Project project;

    public ManualReferenceUpdater(Project project) {
        this.project = project;
    }

    /**
     * Update a use statement to new namespace
     */
    public void updateUseStatement(PhpUse useStatement, String newFqn) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // Create new use statement
            PhpUseList newUseList = PhpPsiElementFactory.createUseStatement(
                project,
                newFqn,
                useStatement.getAliasName()  // preserve alias if exists
            );

            // Replace old with new
            useStatement.getParent().replace(newUseList);
        });
    }

    /**
     * Update a class reference (in extends, implements, type hints, etc.)
     * For namespaced classes: adds a use statement and uses the short class name.
     * For global namespace classes: uses backslash prefix (\ClassName).
     */
    public void updateClassReference(ClassReference classRef, String newFqn) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiFile file = classRef.getContainingFile();
            if (!(file instanceof PhpFile phpFile)) return;

            // Normalize FQN
            String normalizedFqn = newFqn.startsWith("\\") ? newFqn.substring(1) : newFqn;
            String shortName = getShortName(normalizedFqn);

            // Check if the class is in the global namespace
            String classNamespace = extractNamespace(normalizedFqn);
            boolean isGlobalNamespaceClass = classNamespace.isEmpty();

            // Check if the file's namespace matches the class's namespace
            String fileNamespace = getFileNamespace(phpFile);
            boolean sameNamespace = classNamespace.equals(fileNamespace);

            // Get the current reference text to replace
            String oldRefText = classRef.getText();
            if (oldRefText == null) return;

            try {
                // Get text from file (works even without Document)
                String text = file.getText();
                String replacement;
                boolean needsUseStatement = false;

                if (sameNamespace) {
                    // Same namespace - just use the short name
                    replacement = shortName;
                } else if (isGlobalNamespaceClass) {
                    // Global namespace class - use backslash prefix
                    replacement = "\\" + shortName;
                } else {
                    // Different namespace - add use statement and use short name
                    replacement = shortName;
                    // Check if use statement already exists
                    String escapedFqn = java.util.regex.Pattern.quote(normalizedFqn);
                    java.util.regex.Pattern existingUsePattern = java.util.regex.Pattern.compile(
                        "use\\s+\\\\?" + escapedFqn + "\\s*;"
                    );
                    if (!existingUsePattern.matcher(text).find()) {
                        needsUseStatement = true;
                    }
                }

                String newText = text;

                // Add use statement first if needed
                if (needsUseStatement) {
                    String useStatement = "use " + normalizedFqn + ";";
                    newText = insertUseStatement(newText, useStatement);
                }

                // Replace the class reference in text
                // Find and replace the old reference with the new one
                // Use regex to be precise about what we're replacing
                String oldRefPattern = "(?<![\\\\A-Za-z0-9_])" + java.util.regex.Pattern.quote(oldRefText) + "(?![\\\\A-Za-z0-9_])";
                newText = newText.replaceFirst(oldRefPattern, replacement);

                // Write changes if any
                if (!newText.equals(text)) {
                    com.intellij.openapi.vfs.VirtualFile vFile = file.getVirtualFile();
                    if (vFile != null) {
                        vFile.setBinaryContent(newText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }

            } catch (Exception e) {
                com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                    .error("Failed to update class reference", e);
            }
        });
    }

    /**
     * Extract namespace from a fully qualified name.
     */
    private String extractNamespace(String fqn) {
        if (fqn == null) return "";
        int lastSlash = fqn.lastIndexOf('\\');
        return lastSlash >= 0 ? fqn.substring(0, lastSlash) : "";
    }

    /**
     * Get the namespace of a PHP file.
     */
    private String getFileNamespace(PhpFile phpFile) {
        PhpNamespace namespace = getFirstNamespace(phpFile);
        if (namespace != null) {
            String fqn = namespace.getFQN();
            if (fqn != null) {
                return fqn.startsWith("\\") ? fqn.substring(1) : fqn;
            }
        }
        return "";
    }

    /**
     * Add a use statement to a file if not present.
     * Uses document-level text manipulation for reliability.
     */
    public void addUseStatement(PsiFile file, String fqn) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            if (!(file instanceof PhpFile phpFile)) return;

            try {
                com.intellij.openapi.editor.Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                if (document == null) return;

                String text = document.getText();

                // Normalize FQN - remove leading backslash for the use statement
                String normalizedFqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
                String useStatement = "use " + normalizedFqn + ";";

                // Check if there's already a use statement with this FQN (text-based check with regex)
                String escapedFqn = java.util.regex.Pattern.quote(normalizedFqn);
                java.util.regex.Pattern existingUsePattern = java.util.regex.Pattern.compile(
                    "use\\s+\\\\?" + escapedFqn + "\\s*;"
                );
                if (existingUsePattern.matcher(text).find()) return;

                String newText = insertUseStatement(text, useStatement);

                if (!newText.equals(text)) {
                    document.setText(newText);
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                }
            } catch (Exception e) {
                com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                    .error("Failed to add use statement", e);
            }
        });
    }

    /**
     * Update internal references inside a moved file.
     * This handles use statements, class references, and require/include statements
     * that relied on the old file location.
     *
     * Uses document-level text manipulation for reliability.
     *
     * @param movedFile The PHP file that was moved
     * @param oldNamespace The original namespace before move
     * @param newNamespace The new namespace after move
     * @return Number of references updated
     */
    public int updateFileForNamespaceChange(PhpFile movedFile, String oldNamespace, String newNamespace) {
        final int[] updatedCount = {0};

        WriteCommandAction.runWriteCommandAction(project, () -> {
            // First, collect information about what needs to be updated using PSI
            java.util.List<String> globalClassesToPrefix = new java.util.ArrayList<>();

            movedFile.accept(new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (element instanceof ClassReference classRef) {
                        String refName = classRef.getName();
                        String refFqn = classRef.getFQN();
                        String refText = classRef.getText();

                        if (refName != null && refFqn != null && refText != null) {
                            // Handle moving FROM global namespace to a different namespace
                            if (oldNamespace.isEmpty() && !newNamespace.isEmpty()) {
                                // Skip if already fully qualified
                                if (!refText.startsWith("\\")) {
                                    String normalizedFqn = refFqn.startsWith("\\") ? refFqn.substring(1) : refFqn;
                                    boolean isGlobalNamespaceClass = !normalizedFqn.contains("\\");

                                    if (isGlobalNamespaceClass && !globalClassesToPrefix.contains(refName) && !isPrimitiveType(refName)) {
                                        globalClassesToPrefix.add(refName);
                                    }
                                }
                            }
                        }
                    }
                    super.visitElement(element);
                }
            });

            // Now apply ALL changes in a single write
            try {
                String text = movedFile.getText();
                String newText = text;

                // 1. Prefix global namespace class references with backslash
                for (String className : globalClassesToPrefix) {
                    String pattern = "(?<![\\\\A-Za-z0-9_])" + java.util.regex.Pattern.quote(className) + "(?![\\\\A-Za-z0-9_])";
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                    java.util.regex.Matcher m = p.matcher(newText);

                    StringBuffer sb = new StringBuffer();
                    while (m.find()) {
                        int pos = m.start();
                        String before = newText.substring(0, pos);
                        long singleQuotes = before.chars().filter(c -> c == '\'').count();
                        long doubleQuotes = before.chars().filter(c -> c == '"').count();
                        boolean inString = (singleQuotes % 2 == 1) || (doubleQuotes % 2 == 1);

                        if (!inString) {
                            m.appendReplacement(sb, "\\\\" + className);
                            updatedCount[0]++;
                        } else {
                            m.appendReplacement(sb, className);
                        }
                    }
                    m.appendTail(sb);
                    newText = sb.toString();
                }

                // 2. Add or update namespace declaration
                if (newNamespace != null && !newNamespace.isEmpty()) {
                    java.util.regex.Pattern nsPattern = java.util.regex.Pattern.compile(
                        "(namespace\\s+)[A-Za-z_\\\\][A-Za-z0-9_\\\\]*(\\s*;)"
                    );
                    java.util.regex.Matcher matcher = nsPattern.matcher(newText);

                    if (matcher.find()) {
                        // Replace existing namespace
                        newText = matcher.replaceFirst("$1" + newNamespace.replace("\\", "\\\\") + "$2");
                    } else {
                        // No existing namespace - add after <?php tag
                        java.util.regex.Pattern phpTagPattern = java.util.regex.Pattern.compile("(<\\?php\\s*)");
                        java.util.regex.Matcher phpMatcher = phpTagPattern.matcher(newText);
                        if (phpMatcher.find()) {
                            newText = newText.substring(0, phpMatcher.end()) +
                                      "\nnamespace " + newNamespace + ";\n" +
                                      newText.substring(phpMatcher.end());
                        }
                    }
                    updatedCount[0]++;
                }

                // Write all changes at once
                if (!newText.equals(text)) {
                    com.intellij.openapi.vfs.VirtualFile vFile = movedFile.getVirtualFile();
                    if (vFile != null) {
                        vFile.setBinaryContent(newText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                    .error("Failed to update file for namespace change", e);
            }
        });

        return updatedCount[0];
    }

    /**
     * Update internal references inside a moved file (legacy method).
     *
     * @param movedFile The PHP file that was moved
     * @param oldNamespace The original namespace before move
     * @param newNamespace The new namespace after move
     * @return Number of references updated
     */
    public int updateInternalReferences(PhpFile movedFile, String oldNamespace, String newNamespace) {
        final int[] updatedCount = {0};

        WriteCommandAction.runWriteCommandAction(project, () -> {
            // First, collect information about what needs to be updated using PSI
            java.util.List<String> globalClassesToPrefix = new java.util.ArrayList<>();
            java.util.List<String> useStatementsToAdd = new java.util.ArrayList<>();

            movedFile.accept(new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (element instanceof ClassReference classRef) {
                        String refName = classRef.getName();
                        String refFqn = classRef.getFQN();
                        String refText = classRef.getText();

                        if (refName != null && refFqn != null && refText != null) {
                            // Handle moving FROM global namespace to a different namespace
                            if (oldNamespace.isEmpty() && !newNamespace.isEmpty()) {
                                // Skip if already fully qualified
                                if (!refText.startsWith("\\")) {
                                    String normalizedFqn = refFqn.startsWith("\\") ? refFqn.substring(1) : refFqn;
                                    boolean isGlobalNamespaceClass = !normalizedFqn.contains("\\");

                                    if (isGlobalNamespaceClass && !globalClassesToPrefix.contains(refName) && !isPrimitiveType(refName)) {
                                        globalClassesToPrefix.add(refName);
                                    }
                                }
                            }

                            // Check for same-namespace references that need use statements
                            if (!oldNamespace.isEmpty() && !isBuiltInClass(refName)) {
                                if (refFqn.equals(oldNamespace + "\\" + refName) ||
                                    refFqn.equals("\\" + oldNamespace + "\\" + refName)) {
                                    String fullClassName = oldNamespace + "\\" + refName;
                                    if (!useStatementsToAdd.contains(fullClassName)) {
                                        useStatementsToAdd.add(fullClassName);
                                    }
                                }
                            }
                        }
                    }
                    super.visitElement(element);
                }
            });

            // Now apply changes using text manipulation
            try {
                // Get text from PsiFile directly (works even without Document)
                String text = movedFile.getText();
                String newText = text;

                // 1. Prefix global namespace class references with backslash
                for (String className : globalClassesToPrefix) {
                    // Pattern to match unqualified class references
                    // Matches: new ClassName, extends ClassName, implements ClassName,
                    // ClassName::, : ClassName (type hints), catch (ClassName
                    // But NOT: \ClassName (already qualified) or part of FQN like Namespace\ClassName

                    // Match class name that is NOT preceded by \ or another identifier char
                    // and NOT followed by \ (which would make it a namespace prefix)
                    String pattern = "(?<![\\\\A-Za-z0-9_])" + java.util.regex.Pattern.quote(className) + "(?![\\\\A-Za-z0-9_])";
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                    java.util.regex.Matcher m = p.matcher(newText);

                    StringBuffer sb = new StringBuffer();
                    while (m.find()) {
                        // Check if this is in a string literal or comment (basic check)
                        int pos = m.start();
                        String before = newText.substring(0, pos);

                        // Skip if inside a string or comment (basic heuristic)
                        long singleQuotes = before.chars().filter(c -> c == '\'').count();
                        long doubleQuotes = before.chars().filter(c -> c == '"').count();
                        boolean inString = (singleQuotes % 2 == 1) || (doubleQuotes % 2 == 1);

                        if (!inString) {
                            m.appendReplacement(sb, "\\\\" + className);
                            updatedCount[0]++;
                        } else {
                            m.appendReplacement(sb, className);
                        }
                    }
                    m.appendTail(sb);
                    newText = sb.toString();
                }

                // 2. Add use statements for classes that need them
                for (String fqn : useStatementsToAdd) {
                    // Check if already exists in current text
                    String normalizedFqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
                    String escapedFqn = java.util.regex.Pattern.quote(normalizedFqn);
                    java.util.regex.Pattern existingUsePattern = java.util.regex.Pattern.compile(
                        "use\\s+\\\\?" + escapedFqn + "\\s*;"
                    );

                    if (!existingUsePattern.matcher(newText).find()) {
                        String useStatement = "use " + normalizedFqn + ";";
                        newText = insertUseStatement(newText, useStatement);
                        updatedCount[0]++;
                    }
                }

                if (!newText.equals(text)) {
                    // Write directly to VirtualFile (works for newly copied files)
                    com.intellij.openapi.vfs.VirtualFile vFile = movedFile.getVirtualFile();
                    if (vFile != null) {
                        vFile.setBinaryContent(newText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                    .error("Failed to update internal references", e);
            }
        });

        return updatedCount[0];
    }

    /**
     * Insert a use statement into the text at the correct position.
     * Use statements must be placed at file level, NOT inside class bodies.
     */
    private String insertUseStatement(String text, String useStatement) {
        // Find where classes/functions/traits start - use statements must be BEFORE these
        java.util.regex.Pattern classStartPattern = java.util.regex.Pattern.compile(
            "(?:^|\\s)(?:abstract\\s+|final\\s+)?(?:class|interface|trait|enum|function)\\s+",
            java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher classStartMatcher = classStartPattern.matcher(text);
        int classStartPos = classStartMatcher.find() ? classStartMatcher.start() : text.length();

        // Only look at the file-level portion (before any class/function/trait)
        String fileLevelText = text.substring(0, classStartPos);

        // Check for existing file-level use statements - find the LAST one
        java.util.regex.Pattern usePattern = java.util.regex.Pattern.compile("use\\s+[^;]+;");
        java.util.regex.Matcher useMatcher = usePattern.matcher(fileLevelText);

        int lastUseEnd = -1;
        while (useMatcher.find()) {
            lastUseEnd = useMatcher.end();
        }

        if (lastUseEnd > 0) {
            // Insert after the last file-level use statement
            return text.substring(0, lastUseEnd) + "\n" + useStatement + text.substring(lastUseEnd);
        }

        // No existing file-level use statements - check for namespace
        java.util.regex.Pattern nsPattern = java.util.regex.Pattern.compile("namespace\\s+[^;]+;");
        java.util.regex.Matcher nsMatcher = nsPattern.matcher(fileLevelText);

        if (nsMatcher.find()) {
            int insertPos = nsMatcher.end();
            return text.substring(0, insertPos) + "\n\n" + useStatement + text.substring(insertPos);
        }

        // No namespace - insert after <?php tag
        java.util.regex.Pattern phpTagPattern = java.util.regex.Pattern.compile("<\\?php\\s*");
        java.util.regex.Matcher phpMatcher = phpTagPattern.matcher(text);

        if (phpMatcher.find()) {
            int insertPos = phpMatcher.end();
            return text.substring(0, insertPos) + "\n" + useStatement + "\n" + text.substring(insertPos);
        }

        // Fallback - prepend
        return useStatement + "\n" + text;
    }

    /**
     * Internal method to add use statement during a write action.
     * Uses document-level text manipulation for reliability.
     */
    private void addUseStatementInternal(PhpFile phpFile, String fqn) {
        try {
            com.intellij.openapi.editor.Document document = PsiDocumentManager.getInstance(project).getDocument(phpFile);
            if (document == null) return;

            String text = document.getText();

            // Normalize FQN - remove leading backslash for the use statement
            String normalizedFqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
            String useStatement = "use " + normalizedFqn + ";";

            // Check if there's already a use statement with this FQN (text-based check with regex)
            String escapedFqn = java.util.regex.Pattern.quote(normalizedFqn);
            java.util.regex.Pattern existingUsePattern = java.util.regex.Pattern.compile(
                "use\\s+\\\\?" + escapedFqn + "\\s*;"
            );
            if (existingUsePattern.matcher(text).find()) return;

            String newText = insertUseStatement(text, useStatement);

            if (!newText.equals(text)) {
                document.setText(newText);
                PsiDocumentManager.getInstance(project).commitDocument(document);
            }
        } catch (Exception e) {
            com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                .error("Failed to add use statement internally", e);
        }
    }

    /**
     * Update a require/include statement if it contains a relative path.
     *
     * @param include The include/require statement
     * @param oldNamespace The old namespace (used to calculate path difference)
     * @param newNamespace The new namespace (used to calculate path difference)
     * @return true if the statement was updated
     */
    private boolean updateIncludeStatement(Include include, String oldNamespace, String newNamespace) {
        try {
            PsiElement argument = include.getArgument();
            if (argument == null) return false;

            String includeText = argument.getText();
            if (includeText == null) return false;

            // Check if this is a relative path that needs updating
            // Common patterns:
            // - __DIR__ . '/../path/file.php'
            // - dirname(__FILE__) . '/path/file.php'
            // - '../path/file.php'
            // - './path/file.php'

            // Calculate the directory depth difference between old and new namespace
            int oldDepth = oldNamespace.isEmpty() ? 0 : oldNamespace.split("\\\\").length;
            int newDepth = newNamespace.isEmpty() ? 0 : newNamespace.split("\\\\").length;
            int depthDiff = newDepth - oldDepth;

            if (depthDiff == 0) {
                // Same depth, no change needed for relative paths
                return false;
            }

            // Check for patterns with __DIR__ or dirname(__FILE__)
            if (includeText.contains("__DIR__") || includeText.contains("dirname")) {
                // Find the relative path part (e.g., '/../Models/User.php')
                // This is complex because we need to parse the string concatenation

                // Look for string literals in the expression
                java.util.List<StringLiteralExpression> stringLiterals = new java.util.ArrayList<>();
                argument.accept(new PsiRecursiveElementVisitor() {
                    @Override
                    public void visitElement(@NotNull PsiElement element) {
                        if (element instanceof StringLiteralExpression) {
                            stringLiterals.add((StringLiteralExpression) element);
                        }
                        super.visitElement(element);
                    }
                });

                for (StringLiteralExpression literal : stringLiterals) {
                    String path = literal.getContents();
                    if (path != null && (path.contains("../") || path.startsWith("/"))) {
                        String newPath = adjustRelativePath(path, depthDiff);
                        if (!newPath.equals(path)) {
                            // Create new string literal with updated path
                            String quote = literal.getText().startsWith("'") ? "'" : "\"";
                            StringLiteralExpression newLiteral = PhpPsiElementFactory.createStringLiteralExpression(
                                project,
                                newPath,
                                quote.equals("'")
                            );
                            literal.replace(newLiteral);
                            return true;
                        }
                    }
                }
            }

            // Check for simple string literal paths like '../file.php'
            if (argument instanceof StringLiteralExpression) {
                StringLiteralExpression literal = (StringLiteralExpression) argument;
                String path = literal.getContents();
                if (path != null && path.contains("../")) {
                    String newPath = adjustRelativePath(path, depthDiff);
                    if (!newPath.equals(path)) {
                        String quote = literal.getText().startsWith("'") ? "'" : "\"";
                        StringLiteralExpression newLiteral = PhpPsiElementFactory.createStringLiteralExpression(
                            project,
                            newPath,
                            quote.equals("'")
                        );
                        argument.replace(newLiteral);
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            // Log error but don't fail the whole operation
        }
        return false;
    }

    /**
     * Adjust a relative path based on directory depth change.
     *
     * @param path The original relative path
     * @param depthDiff Positive = moved deeper, negative = moved shallower
     * @return The adjusted path
     */
    private String adjustRelativePath(String path, int depthDiff) {
        if (depthDiff > 0) {
            // Moved deeper - need more "../" to go up
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < depthDiff; i++) {
                prefix.append("../");
            }
            // Insert after leading / if present, or at start
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
                result = result.substring(3); // Remove "../"
            }
            // If we removed all "../" and there's no leading slash, add "./"
            if (!result.startsWith("../") && !result.startsWith("/") && !result.startsWith("./")) {
                result = "./" + result;
            }
            return result;
        }
        return path;
    }

    /**
     * Check if a class name is a PHP built-in class.
     */
    private boolean isBuiltInClass(String className) {
        // Common PHP built-in classes that shouldn't have use statements added
        java.util.Set<String> builtIns = java.util.Set.of(
            "Exception", "Error", "Throwable", "RuntimeException",
            "InvalidArgumentException", "LogicException", "OutOfBoundsException",
            "DateTime", "DateTimeImmutable", "DateTimeInterface", "DateInterval", "DateTimeZone",
            "stdClass", "ArrayObject", "ArrayIterator", "Iterator", "IteratorAggregate",
            "Countable", "Serializable", "JsonSerializable", "Stringable",
            "Closure", "Generator", "WeakReference", "WeakMap",
            "PDO", "PDOStatement", "PDOException",
            "ReflectionClass", "ReflectionMethod", "ReflectionProperty", "ReflectionException",
            "SplFileInfo", "SplFileObject", "DirectoryIterator", "RecursiveDirectoryIterator",
            "self", "static", "parent"
        );
        return builtIns.contains(className);
    }

    /**
     * Check if a name is a PHP primitive type (should never be prefixed with \).
     */
    private boolean isPrimitiveType(String name) {
        java.util.Set<String> primitives = java.util.Set.of(
            "array", "bool", "boolean", "callable", "false", "float", "int", "integer",
            "iterable", "mixed", "never", "null", "numeric", "object", "resource",
            "string", "true", "void"
        );
        return primitives.contains(name.toLowerCase());
    }

    /**
     * Update namespace declaration in a file using document-level text replacement.
     * This is more reliable than PSI manipulation for namespace changes.
     */
    public void updateNamespaceDeclaration(PhpFile file, String newNamespace) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                // Get text from PsiFile directly (works even without Document)
                String text = file.getText();
                String newText = null;

                // Pattern to match namespace declaration
                java.util.regex.Pattern nsPattern = java.util.regex.Pattern.compile(
                    "(namespace\\s+)[A-Za-z_\\\\][A-Za-z0-9_\\\\]*(\\s*;)"
                );
                java.util.regex.Matcher matcher = nsPattern.matcher(text);

                if (matcher.find()) {
                    // Replace existing namespace
                    if (newNamespace != null && !newNamespace.isEmpty()) {
                        newText = matcher.replaceFirst("$1" + newNamespace.replace("\\", "\\\\") + "$2");
                    }
                } else if (newNamespace != null && !newNamespace.isEmpty()) {
                    // No existing namespace - add after <?php tag
                    java.util.regex.Pattern phpTagPattern = java.util.regex.Pattern.compile("(<\\?php\\s*)");
                    java.util.regex.Matcher phpMatcher = phpTagPattern.matcher(text);
                    if (phpMatcher.find()) {
                        newText = text.substring(0, phpMatcher.end()) +
                                  "\nnamespace " + newNamespace + ";\n" +
                                  text.substring(phpMatcher.end());
                    }
                }

                if (newText != null && !newText.equals(text)) {
                    // Write directly to VirtualFile (works for newly copied files)
                    com.intellij.openapi.vfs.VirtualFile vFile = file.getVirtualFile();
                    if (vFile != null) {
                        vFile.setBinaryContent(newText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                            .info("Updated namespace to: " + newNamespace + " in file: " + file.getName());
                    }
                }
            } catch (Exception e) {
                com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                    .error("Failed to update namespace in " + file.getName(), e);
            }
        });
    }

    /**
     * Recursively find the namespace reference element.
     */
    private PhpNamespaceReference findNamespaceReferenceRecursive(PsiElement element) {
        if (element instanceof PhpNamespaceReference) {
            return (PhpNamespaceReference) element;
        }
        for (PsiElement child : element.getChildren()) {
            PhpNamespaceReference found = findNamespaceReferenceRecursive(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Find the namespace reference (name) element within a namespace declaration.
     */
    private PhpNamespaceReference findNamespaceReference(PhpNamespace namespace) {
        for (PsiElement child : namespace.getChildren()) {
            if (child instanceof PhpNamespaceReference) {
                return (PhpNamespaceReference) child;
            }
        }
        return null;
    }

    // Helper methods

    private boolean hasUseStatementFor(PsiFile file, String fqn) {
        // Normalize FQN by removing leading backslash for comparison
        String normalizedFqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;

        final boolean[] found = {false};
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PhpUse) {
                    PhpUse use = (PhpUse) element;
                    String useFqn = use.getFQN();
                    if (useFqn != null) {
                        // Normalize the use statement FQN as well
                        String normalizedUseFqn = useFqn.startsWith("\\") ? useFqn.substring(1) : useFqn;
                        if (normalizedFqn.equals(normalizedUseFqn)) {
                            found[0] = true;
                        }
                    }
                }
                super.visitElement(element);
            }
        });
        return found[0];
    }

    private String getShortName(String fqn) {
        int lastSlash = fqn.lastIndexOf('\\');
        return lastSlash >= 0 ? fqn.substring(lastSlash + 1) : fqn;
    }

    private PhpNamespace getFirstNamespace(PhpFile file) {
        for (PsiElement child : file.getChildren()) {
            if (child instanceof PhpNamespace) {
                return (PhpNamespace) child;
            }
        }
        return null;
    }

    private PsiElement findLastUseStatement(PsiElement container) {
        PsiElement last = null;
        for (PsiElement child : container.getChildren()) {
            if (child instanceof PhpUseList) {
                last = child;
            }
        }
        return last;
    }

    /**
     * Find the end of the namespace statement (semicolon or opening brace).
     * This is the proper insertion point for use statements when none exist.
     */
    private PsiElement findNamespaceStatementEnd(PhpNamespace namespace) {
        // Look for semicolon (simple namespace) or opening brace (braced namespace)
        for (PsiElement child : namespace.getChildren()) {
            String text = child.getText();
            if (";".equals(text) || "{".equals(text)) {
                return child;
            }
            // Also check for the namespace reference followed by semicolon
            if (child instanceof PhpNamespaceReference) {
                PsiElement next = child.getNextSibling();
                // Skip whitespace
                while (next != null && next instanceof com.intellij.psi.PsiWhiteSpace) {
                    next = next.getNextSibling();
                }
                if (next != null && ";".equals(next.getText())) {
                    return next;
                }
            }
        }
        return null;
    }
}
