package com.github.psiMcpServer.php;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiFileFactory;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;

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
     */
    public void updateClassReference(ClassReference classRef, String newFqn) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // Determine if we need FQN or short name
            PsiFile file = classRef.getContainingFile();
            boolean hasMatchingUse = hasUseStatementFor(file, newFqn);

            String replacement = hasMatchingUse
                ? getShortName(newFqn)
                : "\\" + newFqn;

            ClassReference newRef = PhpPsiElementFactory.createClassReference(
                project,
                replacement
            );

            classRef.replace(newRef);
        });
    }

    /**
     * Add a use statement to a file if not present
     */
    public void addUseStatement(PsiFile file, String fqn) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            if (!(file instanceof PhpFile)) return;
            PhpFile phpFile = (PhpFile) file;

            // Check if use already exists
            if (hasUseStatementFor(file, fqn)) return;

            // Find or create use list
            PhpNamespace namespace = getFirstNamespace(phpFile);

            PhpUseList newUse = PhpPsiElementFactory.createUseStatement(
                project,
                fqn,
                null
            );

            if (namespace != null) {
                // Add after namespace declaration or last existing use statement
                PsiElement anchor = findLastUseStatement(namespace);
                if (anchor != null) {
                    namespace.addAfter(newUse, anchor);
                } else {
                    // Find the namespace statement end (semicolon or opening brace)
                    PsiElement insertPoint = findNamespaceStatementEnd(namespace);
                    if (insertPoint != null) {
                        namespace.addAfter(newUse, insertPoint);
                    } else {
                        // Fallback: add after first child
                        namespace.addAfter(newUse, namespace.getFirstChild());
                    }
                }
            } else {
                // No namespace, add at top after <?php
                PsiElement anchor = findLastUseStatement(phpFile);
                if (anchor != null) {
                    phpFile.addAfter(newUse, anchor);
                } else {
                    PsiElement openTag = phpFile.getFirstChild();
                    phpFile.addAfter(newUse, openTag);
                }
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
                    // Skip if it's a built-in or already has a use statement
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

                    // Handle moving FROM global namespace to a different namespace:
                    // References to other global namespace classes need to be prefixed with "\"
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
                            }
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
     */
    private void addUseStatementInternal(PhpFile phpFile, String fqn) {
        // Check if use already exists
        if (hasUseStatementFor(phpFile, fqn)) return;

        PhpNamespace namespace = getFirstNamespace(phpFile);

        PhpUseList newUse = PhpPsiElementFactory.createUseStatement(
            project,
            fqn,
            null
        );

        if (namespace != null) {
            PsiElement anchor = findLastUseStatement(namespace);
            if (anchor != null) {
                namespace.addAfter(newUse, anchor);
            } else {
                // Find the namespace statement end (semicolon or opening brace)
                PsiElement insertPoint = findNamespaceStatementEnd(namespace);
                if (insertPoint != null) {
                    namespace.addAfter(newUse, insertPoint);
                } else {
                    namespace.addAfter(newUse, namespace.getFirstChild());
                }
            }
        } else {
            PsiElement anchor = findLastUseStatement(phpFile);
            if (anchor != null) {
                phpFile.addAfter(newUse, anchor);
            } else {
                PsiElement openTag = phpFile.getFirstChild();
                phpFile.addAfter(newUse, openTag);
            }
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
     * Update namespace declaration in a file
     */
    public void updateNamespaceDeclaration(PhpFile file, String newNamespace) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PhpNamespace existingNs = getFirstNamespace(file);

            if (newNamespace == null || newNamespace.isEmpty()) {
                // Moving to global namespace - remove existing namespace if present
                // This is a complex case that would require restructuring the file
                return;
            }

            if (existingNs != null) {
                // Update existing namespace using text replacement approach
                try {
                    // Get the current namespace name from the FQN of any class in the namespace
                    String currentNsName = null;
                    for (PsiElement child : existingNs.getChildren()) {
                        if (child instanceof PhpClass) {
                            String fqn = ((PhpClass) child).getFQN();
                            if (fqn != null) {
                                // Extract namespace from FQN
                                fqn = fqn.startsWith("\\") ? fqn.substring(1) : fqn;
                                int lastSlash = fqn.lastIndexOf('\\');
                                if (lastSlash > 0) {
                                    currentNsName = fqn.substring(0, lastSlash);
                                }
                            }
                            break;
                        }
                    }

                    // Find the namespace reference element (try recursive search)
                    PhpNamespaceReference nsRef = findNamespaceReferenceRecursive(existingNs);
                    if (nsRef != null) {
                        // Create a new namespace reference with the new name
                        String code = "<?php\nnamespace " + newNamespace + ";\nclass Dummy {}";
                        PsiFile tempFile = PsiFileFactory.getInstance(project)
                            .createFileFromText("temp.php", file.getFileType(), code);
                        PhpNamespace tempNs = getFirstNamespace((PhpFile) tempFile);
                        if (tempNs != null) {
                            PhpNamespaceReference newNsRef = findNamespaceReferenceRecursive(tempNs);
                            if (newNsRef != null) {
                                nsRef.replace(newNsRef);
                            }
                        }
                    } else {
                        // Fallback: Try to find and replace text-based
                        // Look for the statement element that contains "namespace"
                        for (PsiElement child : existingNs.getChildren()) {
                            if (child instanceof Statement) {
                                String text = child.getText();
                                if (text != null && text.startsWith("namespace ")) {
                                    // Create replacement statement
                                    String code = "<?php\nnamespace " + newNamespace + ";";
                                    PsiFile tempFile = PsiFileFactory.getInstance(project)
                                        .createFileFromText("temp.php", file.getFileType(), code);
                                    PhpNamespace tempNs = getFirstNamespace((PhpFile) tempFile);
                                    if (tempNs != null) {
                                        for (PsiElement tempChild : tempNs.getChildren()) {
                                            if (tempChild instanceof Statement) {
                                                child.replace(tempChild);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Log error
                    com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                        .error("Failed to update namespace", e);
                }
            } else {
                // No existing namespace - add one at the top of the file
                try {
                    // Find the opening PHP tag
                    PsiElement firstChild = file.getFirstChild();
                    if (firstChild == null) return;

                    // Create namespace statement
                    String code = "<?php\nnamespace " + newNamespace + ";\n";
                    PsiFile tempFile = PsiFileFactory.getInstance(project)
                        .createFileFromText("temp.php", file.getFileType(), code);

                    // Get the namespace element from temp file
                    PhpNamespace newNs = getFirstNamespace((PhpFile) tempFile);
                    if (newNs != null) {
                        // Find where to insert (after <?php tag)
                        PsiElement insertPoint = firstChild;

                        // Skip whitespace after opening tag
                        PsiElement next = insertPoint.getNextSibling();
                        while (next instanceof com.intellij.psi.PsiWhiteSpace) {
                            insertPoint = next;
                            next = next.getNextSibling();
                        }

                        // Add namespace after the insertion point
                        file.addAfter(newNs, insertPoint);
                    }
                } catch (Exception e) {
                    // Log error
                    com.intellij.openapi.diagnostic.Logger.getInstance(ManualReferenceUpdater.class)
                        .error("Failed to add namespace", e);
                }
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
        final boolean[] found = {false};
        file.accept(new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PhpUse) {
                    PhpUse use = (PhpUse) element;
                    if (fqn.equals(use.getFQN())) {
                        found[0] = true;
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
