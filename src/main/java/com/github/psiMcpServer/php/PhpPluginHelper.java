package com.github.psiMcpServer.php;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Helper to check if the PHP plugin is available.
 */
public class PhpPluginHelper {

    private static final Logger LOG = Logger.getInstance(PhpPluginHelper.class);
    private static Boolean phpPluginAvailable = null;

    /**
     * Check if the PHP plugin is available in the current IDE.
     */
    public static boolean isPhpPluginAvailable() {
        if (phpPluginAvailable != null) {
            return phpPluginAvailable;
        }

        try {
            // Try to load a PHP plugin class
            Class.forName("com.jetbrains.php.lang.psi.PhpFile");
            phpPluginAvailable = true;
            LOG.info("PHP plugin detected");
        } catch (ClassNotFoundException e) {
            phpPluginAvailable = false;
            LOG.info("PHP plugin not available");
        }

        return phpPluginAvailable;
    }

    /**
     * Check if a file is a PHP file by extension.
     */
    public static boolean isPhpFile(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".php");
    }
}
