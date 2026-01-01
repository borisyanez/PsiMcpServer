package com.github.psiMcpServer.ui;

import com.github.psiMcpServer.mcp.McpServerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Factory for creating the PSI MCP tool window.
 */
public class McpToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        McpToolWindowPanel panel = new McpToolWindowPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * Panel displaying MCP server status and controls.
     */
    private static class McpToolWindowPanel extends JBPanel<McpToolWindowPanel> {

        private final Project project;
        private final JBLabel statusLabel;
        private final JButton startButton;
        private final JButton stopButton;
        private final Timer refreshTimer;

        public McpToolWindowPanel(Project project) {
            super(new BorderLayout());
            this.project = project;

            // Status panel
            JBPanel<?> statusPanel = new JBPanel<>(new GridLayout(3, 1, 5, 5));
            statusLabel = new JBLabel("Status: Unknown");
            statusPanel.add(statusLabel);

            // Button panel
            JBPanel<?> buttonPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            startButton = new JButton("Start Server");
            stopButton = new JButton("Stop Server");

            startButton.addActionListener(e -> {
                McpServerManager.getInstance(project).startServer();
                updateStatus();
            });

            stopButton.addActionListener(e -> {
                McpServerManager.getInstance(project).stopServer();
                updateStatus();
            });

            buttonPanel.add(startButton);
            buttonPanel.add(stopButton);

            add(statusPanel, BorderLayout.NORTH);
            add(buttonPanel, BorderLayout.CENTER);

            // Refresh timer
            refreshTimer = new Timer(2000, e -> updateStatus());
            refreshTimer.start();

            updateStatus();
        }

        private void updateStatus() {
            McpServerManager manager = McpServerManager.getInstance(project);
            boolean running = manager.isRunning();

            StringBuilder status = new StringBuilder("<html>");
            status.append("<b>Status:</b> ").append(running ? "Running" : "Stopped").append("<br>");
            if (running) {
                status.append("<b>Port:</b> ").append(manager.getPort()).append("<br>");
                status.append("<b>Tools:</b> ").append(manager.getToolCount());
            }
            status.append("</html>");

            statusLabel.setText(status.toString());
            startButton.setEnabled(!running);
            stopButton.setEnabled(running);
        }
    }
}
