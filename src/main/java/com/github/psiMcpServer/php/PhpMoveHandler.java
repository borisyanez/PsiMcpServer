package com.github.psiMcpServer.php;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Handles PHP class moves with proper namespace and reference updates.
 * Only available when the PHP plugin is present (PHPStorm).
 */
public class PhpMoveHandler {

    private final Project project;
    private final ManualReferenceUpdater referenceUpdater;

    public PhpMoveHandler(Project project) {
        this.project = project;
        this.referenceUpdater = new ManualReferenceUpdater(project);
    }

    /**
     * Result of a PHP class move operation.
     */
    public record MoveResult(boolean success, String message, String newFqn, int referencesUpdated) {
        public static MoveResult success(String message, String newFqn, int referencesUpdated) {
            return new MoveResult(true, message, newFqn, referencesUpdated);
        }

        public static MoveResult failure(String message) {
            return new MoveResult(false, message, null, 0);
        }
    }

    /**
     * Move a PHP class to a new namespace/directory.
     *
     * @param sourceFile      The PHP file containing the class
     * @param targetDirectory The target directory
     * @param newNamespace    The new namespace for the class (can be null to auto-detect)
     * @return Result of the move operation
     */
    public MoveResult movePhpClass(PsiFile sourceFile, PsiDirectory targetDirectory, String newNamespace) {
        if (!(sourceFile instanceof PhpFile phpFile)) {
            return MoveResult.failure("Source file is not a PHP file");
        }

        // Find the main class in the file
        PhpClass phpClass = findMainClass(phpFile);
        if (phpClass == null) {
            return MoveResult.failure("No PHP class found in the file");
        }

        String oldFqn = phpClass.getFQN();
        String className = phpClass.getName();

        // Determine new namespace from target directory if not provided
        if (newNamespace == null) {
            newNamespace = detectNamespaceFromDirectory(targetDirectory);
        }

        String newFqn = newNamespace.isEmpty() ? className : newNamespace + "\\" + className;

        // Collect all references before the move
        List<PsiReference> references = collectReferences(phpClass);

        try {
            // 1. Move the file physically
            PsiFile movedFile = moveFile(phpFile, targetDirectory);
            if (movedFile == null) {
                return MoveResult.failure("Failed to move file to target directory");
            }

            // 2. Update namespace declaration in the moved file
            if (movedFile instanceof PhpFile movedPhpFile) {
                updateNamespace(movedPhpFile, newNamespace);
            }

            // 3. Update all references
            int updatedCount = updateReferences(references, oldFqn, newFqn);

            return MoveResult.success(
                "Moved " + className + " to " + newNamespace,
                newFqn,
                updatedCount
            );

        } catch (Exception e) {
            return MoveResult.failure("Move failed: " + e.getMessage());
        }
    }

    /**
     * Find the main class in a PHP file (usually the one matching the filename).
     */
    private PhpClass findMainClass(PhpFile phpFile) {
        return runReadAction(() -> {
            String fileName = phpFile.getName();
            String expectedClassName = fileName.endsWith(".php")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;

            // Search for classes in the file
            final PhpClass[] result = {null};
            phpFile.accept(new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(@org.jetbrains.annotations.NotNull PsiElement element) {
                    if (element instanceof PhpClass phpClass) {
                        if (result[0] == null) {
                            result[0] = phpClass; // First class found
                        }
                        if (expectedClassName.equals(phpClass.getName())) {
                            result[0] = phpClass; // Prefer class matching filename
                        }
                    }
                    super.visitElement(element);
                }
            });

            return result[0];
        });
    }

    /**
     * Detect namespace from directory structure based on PSR-4 conventions.
     */
    private String detectNamespaceFromDirectory(PsiDirectory directory) {
        return runReadAction(() -> {
            // Try to find composer.json to detect PSR-4 autoload mapping
            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return "";
            }

            // Build namespace from directory path relative to common source roots
            String dirPath = directory.getVirtualFile().getPath();
            String projectPath = projectRoot.getPath();

            if (!dirPath.startsWith(projectPath)) {
                return "";
            }

            String relativePath = dirPath.substring(projectPath.length());

            // Common source directories to strip
            String[] sourceRoots = {"/src/", "/app/", "/lib/", "/classes/"};
            for (String root : sourceRoots) {
                if (relativePath.startsWith(root)) {
                    relativePath = relativePath.substring(root.length());
                    break;
                }
            }

            // Convert path to namespace
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            if (relativePath.endsWith("/")) {
                relativePath = relativePath.substring(0, relativePath.length() - 1);
            }

            return relativePath.replace("/", "\\");
        });
    }

    /**
     * Collect all references to a PHP class.
     */
    private List<PsiReference> collectReferences(PhpClass phpClass) {
        return runReadAction(() -> {
            List<PsiReference> references = new ArrayList<>();
            Query<PsiReference> query = ReferencesSearch.search(
                phpClass,
                GlobalSearchScope.projectScope(project)
            );
            references.addAll(query.findAll());
            return references;
        });
    }

    /**
     * Move a file to a target directory.
     */
    private PsiFile moveFile(PsiFile file, PsiDirectory targetDirectory) {
        return WriteCommandAction.runWriteCommandAction(project, (Computable<PsiFile>) () -> {
            try {
                // Check if file already exists in target
                if (targetDirectory.findFile(file.getName()) != null) {
                    return null;
                }

                // Copy to new location
                PsiFile copy = (PsiFile) targetDirectory.add(file.copy());

                // Delete original
                file.delete();

                return copy;
            } catch (Exception e) {
                return null;
            }
        });
    }

    /**
     * Update namespace declaration in a PHP file.
     */
    private void updateNamespace(PhpFile phpFile, String newNamespace) {
        referenceUpdater.updateNamespaceDeclaration(phpFile, newNamespace);
    }

    /**
     * Update all collected references to point to the new FQN.
     */
    private int updateReferences(List<PsiReference> references, String oldFqn, String newFqn) {
        int[] count = {0};

        for (PsiReference reference : references) {
            try {
                PsiElement element = runReadAction(reference::getElement);
                if (element == null || !element.isValid()) {
                    continue;
                }

                if (element instanceof PhpUse useElement) {
                    referenceUpdater.updateUseStatement(useElement, newFqn);
                    count[0]++;
                } else if (element instanceof ClassReference classRef) {
                    referenceUpdater.updateClassReference(classRef, newFqn);
                    count[0]++;
                } else {
                    // For other reference types, try adding a use statement
                    PsiFile containingFile = runReadAction(element::getContainingFile);
                    if (containingFile != null) {
                        referenceUpdater.addUseStatement(containingFile, newFqn);
                        count[0]++;
                    }
                }
            } catch (Exception e) {
                // Skip failed reference updates
            }
        }

        return count[0];
    }

    private <T> T runReadAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }
}
