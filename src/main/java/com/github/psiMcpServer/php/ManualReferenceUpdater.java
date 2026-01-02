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
                // Add after namespace declaration
                PsiElement anchor = findLastUseStatement(namespace);
                if (anchor != null) {
                    namespace.addAfter(newUse, anchor);
                } else {
                    // Add after opening brace or namespace keyword
                    namespace.addAfter(newUse, namespace.getFirstChild());
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
     * Update namespace declaration in a file
     */
    public void updateNamespaceDeclaration(PhpFile file, String newNamespace) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PhpNamespace existingNs = getFirstNamespace(file);

            if (existingNs != null && newNamespace != null && !newNamespace.isEmpty()) {
                // Find and update the namespace name element
                // The namespace structure is: namespace Name\Space;
                // We need to create a new namespace statement and replace the old one
                try {
                    String code = "<?php\nnamespace " + newNamespace + ";";
                    PsiFile tempFile = PsiFileFactory.getInstance(project)
                        .createFileFromText("temp.php", file.getFileType(), code);

                    PhpNamespace newNs = getFirstNamespace((PhpFile) tempFile);
                    if (newNs != null) {
                        // Copy content from old namespace to new
                        for (PsiElement child : existingNs.getChildren()) {
                            if (!(child instanceof com.intellij.psi.PsiWhiteSpace)) {
                                // Skip the namespace keyword and name, copy the rest
                            }
                        }
                        existingNs.replace(newNs);
                    }
                } catch (Exception e) {
                    // Fallback: just log the error
                }
            }
        });
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
}
