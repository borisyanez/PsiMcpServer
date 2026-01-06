package com.github.psiMcpServer.php;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
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
        return movePhpClass(sourceFile, targetDirectory, newNamespace, null);
    }

    /**
     * Move a PHP class to a new namespace/directory with progress reporting.
     *
     * @param sourceFile      The PHP file containing the class
     * @param targetDirectory The target directory
     * @param newNamespace    The new namespace for the class (can be null to auto-detect)
     * @param indicator       Optional progress indicator for reporting status
     * @return Result of the move operation
     */
    public MoveResult movePhpClass(PsiFile sourceFile, PsiDirectory targetDirectory, String newNamespace, ProgressIndicator indicator) {
        if (!(sourceFile instanceof PhpFile phpFile)) {
            return MoveResult.failure("Source file is not a PHP file");
        }

        // Stage 1: Find the main class
        reportProgress(indicator, "Finding main class...", sourceFile.getName());
        PhpClass phpClass = findMainClass(phpFile);
        if (phpClass == null) {
            return MoveResult.failure("No PHP class found in the file");
        }

        String oldFqn = phpClass.getFQN();
        String className = phpClass.getName();

        // Extract old namespace from FQN
        String oldNamespace = extractNamespace(oldFqn);

        // Determine new namespace from target directory if not provided
        if (newNamespace == null) {
            newNamespace = detectNamespaceFromDirectory(targetDirectory);
        }

        String newFqn = newNamespace.isEmpty() ? className : newNamespace + "\\" + className;

        // Stage 2: Collect all references before the move
        reportProgress(indicator, "Collecting references...", className);
        List<PsiReference> references = collectReferences(phpClass);

        try {
            // Stage 3: Move the file physically
            reportProgress(indicator, "Moving file...", className);
            PsiFile movedFile = moveFile(phpFile, targetDirectory);
            if (movedFile == null) {
                return MoveResult.failure("Failed to move file to target directory");
            }

            int updatedCount = 0;

            // Stage 4: Update internal references AND namespace in a single operation
            // Both must be done together because writing to VirtualFile doesn't update
            // PsiFile's cached text, so subsequent reads would get stale content.
            if (movedFile instanceof PhpFile movedPhpFile) {
                reportProgress(indicator, "Updating file content...", className);
                int internalUpdates = referenceUpdater.updateFileForNamespaceChange(
                    movedPhpFile,
                    oldNamespace,
                    newNamespace
                );
                updatedCount += internalUpdates;
            }

            // Stage 5: Update all external references (other files referencing this class)
            reportProgress(indicator, "Updating external references...", className);
            updatedCount += updateReferences(references, oldFqn, newFqn);

            // Stage 6: Update require/include statements that reference the moved file
            reportProgress(indicator, "Updating require/include paths...", className);
            updatedCount += updateRequireIncludePaths(sourceFile, movedFile, className);

            // Stage 7: Clean up duplicate use statements across the project
            reportProgress(indicator, "Cleaning up duplicate imports...", className);
            updatedCount += cleanupDuplicateImports(className);

            // Stage 8: Apply code style fixes using PsiPhpCodeFixer if available
            int codeStyleFixes = 0;
            if (PhpCodeFixerHelper.isPluginAvailable()) {
                reportProgress(indicator, "Applying code style fixes...", className);
                PhpCodeFixerHelper.FixResult fixResult = PhpCodeFixerHelper.fixFile(project, movedFile);
                if (fixResult != null && fixResult.success()) {
                    codeStyleFixes = fixResult.fixCount();
                }
            }

            // Refresh VFS to sync memory with disk (prevents "file out of sync" warnings)
            VirtualFileManager.getInstance().syncRefresh();

            String message = "Moved " + className + " to " + newNamespace;
            if (codeStyleFixes > 0) {
                message += " (+" + codeStyleFixes + " code style fixes)";
            }

            return MoveResult.success(
                message,
                newFqn,
                updatedCount + codeStyleFixes
            );

        } catch (Exception e) {
            return MoveResult.failure("Move failed: " + e.getMessage());
        }
    }

    /**
     * Report progress to the indicator if available.
     */
    private void reportProgress(ProgressIndicator indicator, String stage, String className) {
        if (indicator != null) {
            indicator.setText("Moving PHP class: " + className);
            indicator.setText2(stage);
        }
    }

    /**
     * Extract namespace from a fully qualified name.
     */
    private String extractNamespace(String fqn) {
        if (fqn == null) return "";
        // Remove leading backslash if present
        if (fqn.startsWith("\\")) {
            fqn = fqn.substring(1);
        }
        int lastSlash = fqn.lastIndexOf('\\');
        return lastSlash >= 0 ? fqn.substring(0, lastSlash) : "";
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

    /**
     * Update require/include statements in all project files that reference the moved file.
     *
     * @param originalFile The original source file (before move)
     * @param movedFile The file after being moved
     * @param className The class name (used for filename)
     * @return Number of require/include statements updated
     */
    private int updateRequireIncludePaths(PsiFile originalFile, PsiFile movedFile, String className) {
        int[] count = {0};

        // Calculate old and new paths relative to project
        String projectPath = project.getBasePath();
        if (projectPath == null) return 0;

        String oldFileName = className + ".php";

        // Get the old path from the original file's directory info
        String oldRelativePath = runReadAction(() -> {
            VirtualFile vFile = originalFile.getOriginalFile().getVirtualFile();
            if (vFile == null) return null;
            String fullPath = vFile.getPath();
            if (fullPath.startsWith(projectPath)) {
                String rel = fullPath.substring(projectPath.length());
                if (rel.startsWith("/")) rel = rel.substring(1);
                return rel;
            }
            return null;
        });

        // Get the new path from the moved file
        String newRelativePath = runReadAction(() -> {
            VirtualFile vFile = movedFile.getVirtualFile();
            if (vFile == null) return null;
            String fullPath = vFile.getPath();
            if (fullPath.startsWith(projectPath)) {
                String rel = fullPath.substring(projectPath.length());
                if (rel.startsWith("/")) rel = rel.substring(1);
                return rel;
            }
            return null;
        });

        if (oldRelativePath == null || newRelativePath == null) {
            return 0;
        }

        // Find all PHP files in the project
        VirtualFile projectDir = project.getBaseDir();
        if (projectDir == null) return 0;

        final String finalOldPath = oldRelativePath;
        final String finalNewPath = newRelativePath;

        // First, collect all PHP files in a read action
        List<PhpFile> phpFiles = runReadAction(() -> {
            List<PhpFile> files = new ArrayList<>();
            collectPhpFiles(projectDir, movedFile.getVirtualFile(), files::add);
            return files;
        });

        // Then update each file (write actions happen inside updateRequireIncludePaths)
        for (PhpFile phpFile : phpFiles) {
            int updated = referenceUpdater.updateRequireIncludePaths(
                phpFile,
                oldFileName,
                finalOldPath,
                finalNewPath
            );
            count[0] += updated;
        }

        return count[0];
    }

    /**
     * Recursively collect PHP files from a directory, excluding the moved file.
     */
    private void collectPhpFiles(VirtualFile dir, VirtualFile excludeFile, java.util.function.Consumer<PhpFile> consumer) {
        if (dir == null || !dir.isDirectory()) return;

        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                // Skip common non-source directories
                String name = child.getName();
                if (!name.equals("vendor") && !name.equals("node_modules") && !name.startsWith(".")) {
                    collectPhpFiles(child, excludeFile, consumer);
                }
            } else if (child.getName().endsWith(".php") && !child.equals(excludeFile)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                if (psiFile instanceof PhpFile phpFile) {
                    consumer.accept(phpFile);
                }
            }
        }
    }

    /**
     * Clean up duplicate use statements across the project.
     * Removes old global namespace imports (e.g., "use Cart;") from files that
     * also have the new namespaced import (e.g., "use Entities\Cart;").
     *
     * @param className The short class name that was moved
     * @return Number of files cleaned up
     */
    private int cleanupDuplicateImports(String className) {
        int[] count = {0};

        VirtualFile projectDir = project.getBaseDir();
        if (projectDir == null) return 0;

        // Collect all PHP files in read action
        List<PhpFile> phpFiles = runReadAction(() -> {
            List<PhpFile> files = new ArrayList<>();
            collectPhpFiles(projectDir, null, files::add);
            return files;
        });

        // Clean up each file
        for (PhpFile phpFile : phpFiles) {
            boolean cleaned = referenceUpdater.cleanupDuplicateUseStatements(phpFile, className);
            if (cleaned) {
                count[0]++;
            }
        }

        return count[0];
    }
}
