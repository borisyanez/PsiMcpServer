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

            String replacement;

            if (sameNamespace) {
                // Same namespace - just use the short name
                replacement = shortName;
            } else if (isGlobalNamespaceClass) {
                // Global namespace class - use backslash prefix
                replacement = "\\" + shortName;
            } else {
                // Different namespace - add use statement and use short name
                if (!hasUseStatementFor(file, normalizedFqn)) {
                    addUseStatementInternal(phpFile, normalizedFqn);
                }
                replacement = shortName;
            }

            ClassReference newRef = PhpPsiElementFactory.createClassReference(
                project,
                replacement
            );

            classRef.replace(newRef);
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
            if (!(file instanceof PhpFile)) return;

            // Check if use already exists
            if (hasUseStatementFor(file, fqn)) return;

            try {
                com.intellij.openapi.editor.Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                if (document == null) return;

                String text = document.getText();

                // Normalize FQN - remove leading backslash for the use statement
                String normalizedFqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
                String useStatement = "use " + normalizedFqn + ";";

                // Check if there's already a use statement with this FQN (text-based check with regex)
                // This handles variations like "use \Namespace\Class;" vs "use Namespace\Class;"
                String escapedFqn = java.util.regex.Pattern.quote(normalizedFqn);
                java.util.regex.Pattern existingUsePattern = java.util.regex.Pattern.compile(
                    "use\\s+\\\\?" + escapedFqn + "\\s*;"
                );
                if (existingUsePattern.matcher(text).find()) return;

                String newText = null;

                // Check for existing use statements
                java.util.regex.Pattern usePattern = java.util.regex.Pattern.compile("(use\\s+[^;]+;\\s*)+");
                java.util.regex.Matcher useMatcher = usePattern.matcher(text);

                if (useMatcher.find()) {
                    // Add after the last use statement
                    int insertPos = useMatcher.end();
                    newText = text.substring(0, insertPos) + "\n" + useStatement + text.substring(insertPos);
                } else {
                    // No existing use statements - find where to insert
                    // Check for namespace first
                    java.util.regex.Pattern nsPattern = java.util.regex.Pattern.compile("namespace\\s+[^;]+;\\s*");
                    java.util.regex.Matcher nsMatcher = nsPattern.matcher(text);

                    if (nsMatcher.find()) {
                        // Insert after namespace declaration
                        int insertPos = nsMatcher.end();
                        newText = text.substring(0, insertPos) + "\n" + useStatement + "\n" + text.substring(insertPos);
                    } else {
                        // No namespace - insert after <?php tag
                        java.util.regex.Pattern phpTagPattern = java.util.regex.Pattern.compile("<\\?php\\s*");
                        java.util.regex.Matcher phpMatcher = phpTagPattern.matcher(text);

                        if (phpMatcher.find()) {
                            int insertPos = phpMatcher.end();
                            newText = text.substring(0, insertPos) + "\n" + useStatement + "\n" + text.substring(insertPos);
                        }
                    }
                }

                if (newText != null && !newText.equals(text)) {
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
     * @param movedFile The PHP file that was moved
     * @param oldNamespace The original namespace before move
     * @param newNamespace The new namespace after move
     * @return Number of references updated
     */
    public int updateInternalReferences(PhpFile movedFile, String oldNamespace, String newNamespace) {
        final int[] updatedCount = {0};

        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 1. Collect all use statements, class references, and include/require statements
            java.util.List<PhpUse> useStatements = new java.util.ArrayList<>();
            java.util.List<ClassReference> classReferences = new java.util.ArrayList<>();
            java.util.List<Include> includeStatements = new java.util.ArrayList<>();

            movedFile.accept(new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(@NotNull PsiElement element) {
                    if (element instanceof PhpUse) {
                        useStatements.add((PhpUse) element);
                    } else if (element instanceof ClassReference) {
                        classReferences.add((ClassReference) element);
                    } else if (element instanceof Include) {
                        includeStatements.add((Include) element);
                    }
                    super.visitElement(element);
                }
            });

            // 2. Update require/include statements with relative paths
            for (Include include : includeStatements) {
                if (updateIncludeStatement(include, oldNamespace, newNamespace)) {
                    updatedCount[0]++;
                }
            }

            // 2. Update use statements that were relative to old namespace
            for (PhpUse useStatement : useStatements) {
                String useFqn = useStatement.getFQN();
                if (useFqn != null) {
                    // Check if this use statement was importing from the old namespace (sibling classes)
                    if (useFqn.startsWith(oldNamespace + "\\")) {
                        // This was a sibling class - it stays the same (absolute FQN)
                        // No change needed
                    }
                    // Check for relative imports that might have been resolved against old namespace
                    // These would already be stored as FQN, so they should be fine
                }
            }

            // 3. Check for unqualified class references that relied on same-namespace resolution
            for (ClassReference classRef : classReferences) {
                String refName = classRef.getName();
                String refFqn = classRef.getFQN();

                if (refName != null && refFqn != null) {
                    // Handle moving FROM global namespace to a different namespace:
                    // References to other global namespace classes need to be prefixed with "\"
                    // This applies to ALL classes (including built-ins like DateTime, Exception)
                    if (oldNamespace.isEmpty() && !newNamespace.isEmpty()) {
                        // Check if the reference is unqualified (short name) and resolves to global namespace
                        String refText = classRef.getText();
                        // Skip if already fully qualified (starts with \)
                        if (refText != null && !refText.startsWith("\\")) {
                            // Check if this class is in the global namespace
                            // FQN for global namespace classes is just the class name (no leading backslash in getFQN())
                            // or might be \ClassName
                            String normalizedFqn = refFqn.startsWith("\\") ? refFqn.substring(1) : refFqn;
                            boolean isGlobalNamespaceClass = !normalizedFqn.contains("\\");

                            if (isGlobalNamespaceClass) {
                                // Prefix with backslash to make it explicitly global
                                ClassReference newRef = PhpPsiElementFactory.createClassReference(
                                    project,
                                    "\\" + refName
                                );
                                classRef.replace(newRef);
                                updatedCount[0]++;
                                continue; // Already handled this reference
                            }
                        }
                    }

                    // Skip built-in classes for the remaining logic (use statement handling)
                    if (isBuiltInClass(refName)) {
                        continue;
                    }

                    // Check if this was a same-namespace reference (short name resolved to old namespace)
                    if (!oldNamespace.isEmpty() && refFqn.equals(oldNamespace + "\\" + refName)) {
                        // This class was in the old namespace - need to add a use statement
                        // because after the move, the short name won't resolve to it anymore
                        String fullClassName = oldNamespace + "\\" + refName;

                        // Check if we already have a use statement for this
                        if (!hasUseStatementFor(movedFile, fullClassName)) {
                            // Add use statement for the old sibling class
                            addUseStatementInternal(movedFile, fullClassName);
                            updatedCount[0]++;
                        }
                    }

                    // Check if the reference is to a class in the NEW namespace
                    // (i.e., a class that was also moved to the same location)
                    // In this case, no use statement is needed
                }
            }
        });

        return updatedCount[0];
    }

    /**
     * Internal method to add use statement during a write action.
     * Uses document-level text manipulation for reliability.
     */
    private void addUseStatementInternal(PhpFile phpFile, String fqn) {
        // Check if use already exists
        if (hasUseStatementFor(phpFile, fqn)) return;

        try {
            com.intellij.openapi.editor.Document document = PsiDocumentManager.getInstance(project).getDocument(phpFile);
            if (document == null) return;

            String text = document.getText();

            // Normalize FQN - remove leading backslash for the use statement
            String normalizedFqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
            String useStatement = "use " + normalizedFqn + ";";

            // Check if there's already a use statement with this FQN (text-based check with regex)
            // This handles variations like "use \Namespace\Class;" vs "use Namespace\Class;"
            String escapedFqn = java.util.regex.Pattern.quote(normalizedFqn);
            java.util.regex.Pattern existingUsePattern = java.util.regex.Pattern.compile(
                "use\\s+\\\\?" + escapedFqn + "\\s*;"
            );
            if (existingUsePattern.matcher(text).find()) return;

            String newText = null;

            // Check for existing use statements
            java.util.regex.Pattern usePattern = java.util.regex.Pattern.compile("(use\\s+[^;]+;\\s*)+");
            java.util.regex.Matcher useMatcher = usePattern.matcher(text);

            if (useMatcher.find()) {
                // Add after the last use statement
                int insertPos = useMatcher.end();
                newText = text.substring(0, insertPos) + "\n" + useStatement + text.substring(insertPos);
            } else {
                // No existing use statements - find where to insert
                // Check for namespace first
                java.util.regex.Pattern nsPattern = java.util.regex.Pattern.compile("namespace\\s+[^;]+;\\s*");
                java.util.regex.Matcher nsMatcher = nsPattern.matcher(text);

                if (nsMatcher.find()) {
                    // Insert after namespace declaration
                    int insertPos = nsMatcher.end();
                    newText = text.substring(0, insertPos) + "\n" + useStatement + "\n" + text.substring(insertPos);
                } else {
                    // No namespace - insert after <?php tag
                    java.util.regex.Pattern phpTagPattern = java.util.regex.Pattern.compile("<\\?php\\s*");
                    java.util.regex.Matcher phpMatcher = phpTagPattern.matcher(text);

                    if (phpMatcher.find()) {
                        int insertPos = phpMatcher.end();
                        newText = text.substring(0, insertPos) + "\n" + useStatement + "\n" + text.substring(insertPos);
                    }
                }
            }

            if (newText != null && !newText.equals(text)) {
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
     * Update namespace declaration in a file using document-level text replacement.
     * This is more reliable than PSI manipulation for namespace changes.
     */
    public void updateNamespaceDeclaration(PhpFile file, String newNamespace) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                com.intellij.openapi.editor.Document document = PsiDocumentManager.getInstance(project).getDocument(file);
                if (document == null) {
                    com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                        .warn("Could not get document for file: " + file.getName());
                    return;
                }

                String text = document.getText();
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
                        String phpTag = phpMatcher.group(1);
                        newText = text.substring(0, phpMatcher.end()) +
                                  "\nnamespace " + newNamespace + ";\n" +
                                  text.substring(phpMatcher.end());
                    }
                }

                if (newText != null && !newText.equals(text)) {
                    document.setText(newText);
                    PsiDocumentManager.getInstance(project).commitDocument(document);
                    com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                        .info("Updated namespace to: " + newNamespace + " in file: " + file.getName());
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
