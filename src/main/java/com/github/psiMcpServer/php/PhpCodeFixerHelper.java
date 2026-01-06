package com.github.psiMcpServer.php;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Helper class to integrate with PsiPhpCodeFixer plugin if available.
 * Uses reflection to avoid hard dependency on the plugin.
 */
public class PhpCodeFixerHelper {

    private static final Logger LOG = Logger.getInstance(PhpCodeFixerHelper.class);
    private static Boolean pluginAvailable = null;
    private static Object apiInstance = null;
    private static Method fixFileMethod = null;

    /**
     * Check if the PsiPhpCodeFixer plugin is available.
     */
    public static boolean isPluginAvailable() {
        if (pluginAvailable == null) {
            try {
                Class<?> apiClass = Class.forName("com.github.psiPhpCodeFixer.api.PhpCsFixerApi");
                Method getInstanceMethod = apiClass.getMethod("getInstance");
                apiInstance = getInstanceMethod.invoke(null);
                fixFileMethod = apiClass.getMethod("fixFile", Project.class, String.class);
                pluginAvailable = true;
                LOG.info("PsiPhpCodeFixer plugin is available");
            } catch (ClassNotFoundException e) {
                pluginAvailable = false;
                LOG.info("PsiPhpCodeFixer plugin is not installed");
            } catch (Exception e) {
                pluginAvailable = false;
                LOG.warn("Failed to initialize PsiPhpCodeFixer API: " + e.getMessage());
            }
        }
        return pluginAvailable;
    }

    /**
     * Fix a PHP file using the PsiPhpCodeFixer plugin.
     *
     * @param project  the current project
     * @param filePath absolute path to the PHP file
     * @return FixResult containing fix information, or null if plugin not available
     */
    @Nullable
    public static FixResult fixFile(@NotNull Project project, @NotNull String filePath) {
        if (!isPluginAvailable()) {
            return null;
        }

        try {
            Object result = fixFileMethod.invoke(apiInstance, project, filePath);
            return parseFixResult(result);
        } catch (Exception e) {
            LOG.warn("Failed to fix file with PsiPhpCodeFixer: " + e.getMessage());
            return new FixResult(false, 0, "Error: " + e.getMessage());
        }
    }

    /**
     * Fix a PHP file using the PsiPhpCodeFixer plugin.
     *
     * @param project the current project
     * @param file    the PHP file to fix
     * @return FixResult containing fix information, or null if plugin not available
     */
    @Nullable
    public static FixResult fixFile(@NotNull Project project, @NotNull PsiFile file) {
        if (file.getVirtualFile() == null) {
            return null;
        }
        return fixFile(project, file.getVirtualFile().getPath());
    }

    /**
     * Parse the FixResult from the PsiPhpCodeFixer API using reflection.
     */
    private static FixResult parseFixResult(Object result) {
        try {
            Class<?> resultClass = result.getClass();

            Method isSuccessMethod = resultClass.getMethod("isSuccess");
            boolean success = (Boolean) isSuccessMethod.invoke(result);

            Method getFixCountMethod = resultClass.getMethod("getFixCount");
            int fixCount = (Integer) getFixCountMethod.invoke(result);

            String message;
            if (success) {
                message = fixCount > 0
                    ? "Applied " + fixCount + " code style fixes"
                    : "No code style issues found";
            } else {
                Method getErrorMethod = resultClass.getMethod("getError");
                Object error = getErrorMethod.invoke(result);
                message = error != null ? error.toString() : "Unknown error";
            }

            return new FixResult(success, fixCount, message);
        } catch (Exception e) {
            LOG.warn("Failed to parse FixResult: " + e.getMessage());
            return new FixResult(false, 0, "Failed to parse result: " + e.getMessage());
        }
    }

    /**
     * Result of a code fix operation.
     */
    public record FixResult(boolean success, int fixCount, String message) {
        public boolean hasChanges() {
            return fixCount > 0;
        }
    }
}
