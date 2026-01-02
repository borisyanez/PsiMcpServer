package com.github.psiMcpServer.php;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.lang.psi.PhpFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Handles batch operations for moving multiple PHP classes.
 * Only available when the PHP plugin is present (PHPStorm).
 */
public class PhpBatchMoveHandler {

    private final Project project;
    private final PhpMoveHandler singleMoveHandler;

    public PhpBatchMoveHandler(Project project) {
        this.project = project;
        this.singleMoveHandler = new PhpMoveHandler(project);
    }

    /**
     * Result of a batch move operation.
     */
    public record BatchMoveResult(
        boolean success,
        String message,
        int totalFiles,
        int movedFiles,
        int failedFiles,
        List<FileMoveResult> details
    ) {
        public static BatchMoveResult success(int total, int moved, List<FileMoveResult> details) {
            return new BatchMoveResult(
                true,
                "Moved " + moved + " of " + total + " files",
                total,
                moved,
                total - moved,
                details
            );
        }

        public static BatchMoveResult failure(String message) {
            return new BatchMoveResult(false, message, 0, 0, 0, List.of());
        }
    }

    /**
     * Result for a single file in the batch.
     */
    public record FileMoveResult(
        String originalPath,
        String newPath,
        String newFqn,
        boolean success,
        String message,
        int referencesUpdated
    ) {}

    /**
     * Move all PHP files from a source directory to a target directory.
     *
     * @param sourceDirectory The source directory containing PHP files
     * @param targetDirectory The target directory
     * @param newNamespaceBase The base namespace for moved classes (null for auto-detect)
     * @param recursive Whether to include subdirectories
     * @return Result of the batch operation
     */
    public BatchMoveResult moveDirectory(
        PsiDirectory sourceDirectory,
        PsiDirectory targetDirectory,
        String newNamespaceBase,
        boolean recursive
    ) {
        List<PsiFile> phpFiles = collectPhpFiles(sourceDirectory, recursive);

        if (phpFiles.isEmpty()) {
            return BatchMoveResult.failure("No PHP files found in source directory");
        }

        return moveFiles(phpFiles, sourceDirectory, targetDirectory, newNamespaceBase, recursive);
    }

    /**
     * Move PHP files matching a glob pattern.
     *
     * @param sourceDirectory The source directory to search
     * @param pattern Glob pattern (e.g., "*Controller.php", "Service*.php")
     * @param targetDirectory The target directory
     * @param newNamespaceBase The base namespace for moved classes
     * @param recursive Whether to search subdirectories
     * @return Result of the batch operation
     */
    public BatchMoveResult moveByPattern(
        PsiDirectory sourceDirectory,
        String pattern,
        PsiDirectory targetDirectory,
        String newNamespaceBase,
        boolean recursive
    ) {
        Pattern regex = globToRegex(pattern);
        List<PsiFile> matchingFiles = collectPhpFilesByPattern(sourceDirectory, regex, recursive);

        if (matchingFiles.isEmpty()) {
            return BatchMoveResult.failure("No PHP files matching pattern '" + pattern + "' found");
        }

        return moveFiles(matchingFiles, sourceDirectory, targetDirectory, newNamespaceBase, recursive);
    }

    /**
     * Move a specific list of PHP files.
     *
     * @param files List of PHP files to move
     * @param targetDirectory The target directory
     * @param newNamespaceBase The base namespace for moved classes
     * @return Result of the batch operation
     */
    public BatchMoveResult moveFiles(
        List<PsiFile> files,
        PsiDirectory sourceDirectory,
        PsiDirectory targetDirectory,
        String newNamespaceBase,
        boolean preserveStructure
    ) {
        List<FileMoveResult> results = new ArrayList<>();
        int movedCount = 0;

        for (PsiFile file : files) {
            if (!(file instanceof PhpFile)) {
                results.add(new FileMoveResult(
                    getFilePath(file),
                    null,
                    null,
                    false,
                    "Not a PHP file",
                    0
                ));
                continue;
            }

            try {
                // Determine target directory (preserving structure if needed)
                PsiDirectory actualTarget = targetDirectory;
                String namespace = newNamespaceBase;

                if (preserveStructure && sourceDirectory != null) {
                    String relativePath = getRelativePath(sourceDirectory, file.getContainingDirectory());
                    if (relativePath != null && !relativePath.isEmpty()) {
                        actualTarget = getOrCreateSubdirectory(targetDirectory, relativePath);
                        if (namespace != null && !namespace.isEmpty()) {
                            namespace = namespace + "\\" + relativePath.replace("/", "\\");
                        }
                    }
                }

                String originalPath = getFilePath(file);
                PhpMoveHandler.MoveResult moveResult = singleMoveHandler.movePhpClass(
                    file,
                    actualTarget,
                    namespace
                );

                if (moveResult.success()) {
                    movedCount++;
                    results.add(new FileMoveResult(
                        originalPath,
                        getFilePath(actualTarget) + "/" + file.getName(),
                        moveResult.newFqn(),
                        true,
                        moveResult.message(),
                        moveResult.referencesUpdated()
                    ));
                } else {
                    results.add(new FileMoveResult(
                        originalPath,
                        null,
                        null,
                        false,
                        moveResult.message(),
                        0
                    ));
                }
            } catch (Exception e) {
                results.add(new FileMoveResult(
                    getFilePath(file),
                    null,
                    null,
                    false,
                    "Error: " + e.getMessage(),
                    0
                ));
            }
        }

        return BatchMoveResult.success(files.size(), movedCount, results);
    }

    /**
     * Collect all PHP files in a directory.
     */
    private List<PsiFile> collectPhpFiles(PsiDirectory directory, boolean recursive) {
        return runReadAction(() -> {
            List<PsiFile> files = new ArrayList<>();
            collectPhpFilesRecursive(directory, files, recursive);
            return files;
        });
    }

    private void collectPhpFilesRecursive(PsiDirectory directory, List<PsiFile> files, boolean recursive) {
        for (PsiFile file : directory.getFiles()) {
            if (file instanceof PhpFile) {
                files.add(file);
            }
        }

        if (recursive) {
            for (PsiDirectory subDir : directory.getSubdirectories()) {
                collectPhpFilesRecursive(subDir, files, true);
            }
        }
    }

    /**
     * Collect PHP files matching a pattern.
     */
    private List<PsiFile> collectPhpFilesByPattern(PsiDirectory directory, Pattern pattern, boolean recursive) {
        return runReadAction(() -> {
            List<PsiFile> files = new ArrayList<>();
            collectPhpFilesByPatternRecursive(directory, pattern, files, recursive);
            return files;
        });
    }

    private void collectPhpFilesByPatternRecursive(
        PsiDirectory directory,
        Pattern pattern,
        List<PsiFile> files,
        boolean recursive
    ) {
        for (PsiFile file : directory.getFiles()) {
            if (file instanceof PhpFile && pattern.matcher(file.getName()).matches()) {
                files.add(file);
            }
        }

        if (recursive) {
            for (PsiDirectory subDir : directory.getSubdirectories()) {
                collectPhpFilesByPatternRecursive(subDir, pattern, files, true);
            }
        }
    }

    /**
     * Convert glob pattern to regex.
     */
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

    /**
     * Get relative path from parent to child directory.
     */
    private String getRelativePath(PsiDirectory parent, PsiDirectory child) {
        if (child == null || parent == null) {
            return null;
        }

        String parentPath = parent.getVirtualFile().getPath();
        String childPath = child.getVirtualFile().getPath();

        if (childPath.startsWith(parentPath)) {
            String relative = childPath.substring(parentPath.length());
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return relative;
        }

        return null;
    }

    /**
     * Get or create a subdirectory path.
     */
    private PsiDirectory getOrCreateSubdirectory(PsiDirectory base, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return base;
        }

        String[] parts = relativePath.split("/");
        PsiDirectory current = base;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            PsiDirectory subDir = current.findSubdirectory(part);
            if (subDir == null) {
                subDir = current.createSubdirectory(part);
            }
            current = subDir;
        }

        return current;
    }

    private String getFilePath(PsiFile file) {
        return runReadAction(() -> {
            VirtualFile vf = file.getVirtualFile();
            return vf != null ? vf.getPath() : file.getName();
        });
    }

    private String getFilePath(PsiDirectory directory) {
        return runReadAction(() -> directory.getVirtualFile().getPath());
    }

    private <T> T runReadAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }
}
