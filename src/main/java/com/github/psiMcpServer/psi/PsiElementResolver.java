package com.github.psiMcpServer.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for resolving PSI elements from file paths and names.
 * All operations run within read actions for thread safety.
 */
public class PsiElementResolver {

    private final Project project;

    public PsiElementResolver(@NotNull Project project) {
        this.project = project;
    }

    private <T> T runReadAction(Computable<T> computable) {
        return ApplicationManager.getApplication().runReadAction(computable);
    }

    /**
     * Resolve a PsiFile from an absolute file path.
     */
    public Optional<PsiFile> resolveFile(@NotNull String absolutePath) {
        return runReadAction(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath);
            if (virtualFile == null || !virtualFile.isValid()) {
                return Optional.<PsiFile>empty();
            }
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            return Optional.ofNullable(psiFile);
        });
    }

    /**
     * Resolve a VirtualFile from an absolute file path.
     */
    public Optional<VirtualFile> resolveVirtualFile(@NotNull String absolutePath) {
        return Optional.ofNullable(LocalFileSystem.getInstance().findFileByPath(absolutePath));
    }

    /**
     * Find a PSI element at a specific offset in a file.
     */
    public Optional<PsiElement> findElementAtOffset(@NotNull String filePath, int offset) {
        Optional<PsiFile> fileOpt = resolveFile(filePath);
        if (fileOpt.isEmpty()) {
            return Optional.empty();
        }
        PsiFile psiFile = fileOpt.get();
        return runReadAction(() -> Optional.ofNullable(psiFile.findElementAt(offset)));
    }

    /**
     * Find all named elements with a given name in a file.
     */
    public List<PsiNamedElement> findNamedElements(@NotNull String filePath, @NotNull String elementName) {
        Optional<PsiFile> fileOpt = resolveFile(filePath);
        if (fileOpt.isEmpty()) {
            return List.of();
        }
        PsiFile psiFile = fileOpt.get();
        return runReadAction(() -> {
            List<PsiNamedElement> result = new ArrayList<>();
            Collection<PsiNamedElement> elements = PsiTreeUtil.collectElementsOfType(
                psiFile, PsiNamedElement.class);
            for (PsiNamedElement element : elements) {
                if (elementName.equals(element.getName())) {
                    result.add(element);
                }
            }
            return result;
        });
    }

    /**
     * Find the first named element with a given name in a file.
     */
    public Optional<PsiNamedElement> findNamedElement(@NotNull String filePath, @NotNull String elementName) {
        List<PsiNamedElement> elements = findNamedElements(filePath, elementName);
        return elements.isEmpty() ? Optional.empty() : Optional.of(elements.get(0));
    }

    /**
     * Find a named element at or near a specific offset.
     * Traverses up the PSI tree to find the nearest named element.
     */
    public Optional<PsiNamedElement> findNamedElementAtOffset(@NotNull String filePath, int offset) {
        Optional<PsiElement> elementOpt = findElementAtOffset(filePath, offset);
        if (elementOpt.isEmpty()) {
            return Optional.empty();
        }
        PsiElement element = elementOpt.get();
        return runReadAction(() -> {
            PsiElement current = element;
            while (current != null) {
                if (current instanceof PsiNamedElement named) {
                    return Optional.of(named);
                }
                current = current.getParent();
            }
            return Optional.<PsiNamedElement>empty();
        });
    }

    /**
     * Get the containing directory of a file.
     */
    public Optional<PsiDirectory> getContainingDirectory(@NotNull String filePath) {
        Optional<PsiFile> fileOpt = resolveFile(filePath);
        if (fileOpt.isEmpty()) {
            return Optional.empty();
        }
        PsiFile psiFile = fileOpt.get();
        return runReadAction(() -> Optional.ofNullable(psiFile.getContainingDirectory()));
    }

    /**
     * Find a directory by path.
     */
    public Optional<PsiDirectory> findDirectory(@NotNull String directoryPath) {
        return runReadAction(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(directoryPath);
            if (virtualFile == null || !virtualFile.isDirectory()) {
                return Optional.<PsiDirectory>empty();
            }
            PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
            return Optional.ofNullable(psiDirectory);
        });
    }

    /**
     * Get all named elements in a file.
     */
    public List<PsiNamedElement> getAllNamedElements(@NotNull String filePath) {
        Optional<PsiFile> fileOpt = resolveFile(filePath);
        if (fileOpt.isEmpty()) {
            return List.of();
        }
        PsiFile psiFile = fileOpt.get();
        return runReadAction(() -> {
            Collection<PsiNamedElement> elements = PsiTreeUtil.collectElementsOfType(
                psiFile, PsiNamedElement.class);
            return new ArrayList<>(elements);
        });
    }

    /**
     * Get information about a PSI element.
     */
    @Nullable
    public ElementInfo getElementInfo(@NotNull PsiElement element) {
        return runReadAction(() -> {
            if (!element.isValid()) {
                return null;
            }

            String name = element instanceof PsiNamedElement named ? named.getName() : null;
            String type = element.getClass().getSimpleName();
            PsiFile file = element.getContainingFile();
            String filePath = file != null && file.getVirtualFile() != null
                ? file.getVirtualFile().getPath()
                : null;
            int offset = element.getTextOffset();
            int length = element.getTextLength();

            return new ElementInfo(name, type, filePath, offset, length);
        });
    }

    /**
     * Get the project's global search scope.
     */
    public GlobalSearchScope getProjectScope() {
        return GlobalSearchScope.projectScope(project);
    }

    /**
     * Information about a PSI element.
     */
    public record ElementInfo(
        @Nullable String name,
        @NotNull String type,
        @Nullable String filePath,
        int offset,
        int length
    ) {}
}
