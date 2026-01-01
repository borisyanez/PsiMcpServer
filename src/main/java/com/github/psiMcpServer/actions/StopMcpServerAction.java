package com.github.psiMcpServer.actions;

import com.github.psiMcpServer.mcp.McpServerManager;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Action to stop the MCP server for the current project.
 */
public class StopMcpServerAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        McpServerManager manager = McpServerManager.getInstance(project);

        if (!manager.isRunning()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("PSI MCP Notifications")
                .createNotification(
                    "MCP Server is not running",
                    NotificationType.INFORMATION
                )
                .notify(project);
            return;
        }

        try {
            manager.stopServer();
            NotificationGroupManager.getInstance()
                .getNotificationGroup("PSI MCP Notifications")
                .createNotification(
                    "MCP Server Stopped",
                    NotificationType.INFORMATION
                )
                .notify(project);
        } catch (Exception ex) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("PSI MCP Notifications")
                .createNotification(
                    "Failed to stop MCP Server",
                    ex.getMessage(),
                    NotificationType.ERROR
                )
                .notify(project);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        McpServerManager manager = McpServerManager.getInstance(project);
        e.getPresentation().setEnabled(manager.isRunning());
    }
}
