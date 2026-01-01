package com.github.psiMcpServer.actions;

import com.github.psiMcpServer.mcp.McpServerManager;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Action to show the current status of the MCP server.
 */
public class ShowServerStatusAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        McpServerManager manager = McpServerManager.getInstance(project);

        StringBuilder status = new StringBuilder();
        status.append("Project: ").append(project.getName()).append("\n");
        status.append("Server Status: ").append(manager.isRunning() ? "Running" : "Stopped").append("\n");

        if (manager.isRunning()) {
            status.append("Initialized: ").append(manager.isInitialized() ? "Yes" : "No").append("\n");
            status.append("Tools Available: ").append(manager.getToolCount());
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("PSI MCP Notifications")
            .createNotification(
                "PSI MCP Server Status",
                status.toString(),
                NotificationType.INFORMATION
            )
            .notify(project);
    }
}
