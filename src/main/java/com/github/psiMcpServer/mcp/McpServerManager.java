package com.github.psiMcpServer.mcp;

import com.github.psiMcpServer.settings.PsiMcpSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Manages the MCP server lifecycle for a project.
 * Uses a socket-based server that external MCP proxies can connect to.
 */
@Service(Service.Level.PROJECT)
public final class McpServerManager implements Disposable {

    private static final Logger LOG = Logger.getInstance(McpServerManager.class);

    private final Project project;
    private McpSocketServer socketServer;

    public McpServerManager(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Start the MCP socket server.
     */
    public void startServer() {
        if (socketServer != null && socketServer.isRunning()) {
            LOG.warn("MCP server is already running");
            return;
        }

        int port = PsiMcpSettings.getInstance().getPort();
        socketServer = new McpSocketServer(project);

        try {
            socketServer.start(port);
            LOG.info("MCP server started on port " + port + " for project: " + project.getName());
        } catch (IOException e) {
            LOG.error("Failed to start MCP server on port " + port, e);
            throw new RuntimeException("Failed to start MCP server: " + e.getMessage(), e);
        }
    }

    /**
     * Stop the MCP server.
     */
    public void stopServer() {
        if (socketServer != null) {
            socketServer.stop();
            socketServer = null;
            LOG.info("MCP server stopped");
        }
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return socketServer != null && socketServer.isRunning();
    }

    /**
     * Check if any client has initialized.
     */
    public boolean isInitialized() {
        return isRunning(); // Simplified - socket server handles multiple clients
    }

    /**
     * Get the port the server is running on.
     */
    public int getPort() {
        return socketServer != null ? socketServer.getPort() : PsiMcpSettings.getInstance().getPort();
    }

    /**
     * Get the number of registered tools.
     */
    public int getToolCount() {
        return socketServer != null ? socketServer.getToolCount() : 0;
    }

    /**
     * Get the names of all registered tools.
     */
    public java.util.List<String> getToolNames() {
        return socketServer != null ? socketServer.getToolNames() : java.util.List.of();
    }

    public static McpServerManager getInstance(@NotNull Project project) {
        return project.getService(McpServerManager.class);
    }

    @Override
    public void dispose() {
        stopServer();
    }
}
