package com.github.psiMcpServer;

import com.github.psiMcpServer.mcp.McpServerManager;
import com.github.psiMcpServer.settings.PsiMcpSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Startup activity that optionally starts the MCP server when the project opens.
 */
public class PsiMcpStartupActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(PsiMcpStartupActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        PsiMcpSettings settings = PsiMcpSettings.getInstance();

        if (settings.isAutoStartServer()) {
            LOG.info("Auto-starting MCP server for project: " + project.getName());
            try {
                McpServerManager.getInstance(project).startServer();
            } catch (Exception e) {
                LOG.error("Failed to auto-start MCP server", e);
            }
        }

        return Unit.INSTANCE;
    }
}
