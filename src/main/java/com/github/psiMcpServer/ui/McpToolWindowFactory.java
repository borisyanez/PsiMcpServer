package com.github.psiMcpServer.ui;

import com.github.psiMcpServer.mcp.McpServerManager;
import com.github.psiMcpServer.php.PhpCodeFixerHelper;
import com.github.psiMcpServer.php.PhpPluginHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

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
        private final JBLabel toolsLabel;
        private final JButton startButton;
        private final JButton stopButton;
        private final Timer refreshTimer;

        public McpToolWindowPanel(Project project) {
            super(new BorderLayout(0, 10));
            this.project = project;
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Status panel
            JBPanel<?> statusPanel = new JBPanel<>(new BorderLayout(0, 5));
            statusLabel = new JBLabel("Status: Unknown");
            statusPanel.add(statusLabel, BorderLayout.NORTH);

            // Tools list
            toolsLabel = new JBLabel("");
            JBScrollPane scrollPane = new JBScrollPane(toolsLabel);
            scrollPane.setPreferredSize(new Dimension(300, 200));
            scrollPane.setBorder(BorderFactory.createTitledBorder("Available Tools"));
            statusPanel.add(scrollPane, BorderLayout.CENTER);

            // Button panel
            JBPanel<?> buttonPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT));
            startButton = new JButton("Start PSI MCP Server");
            stopButton = new JButton("Stop PSI MCP Server");

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

            add(statusPanel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);

            // Refresh timer
            refreshTimer = new Timer(2000, e -> updateStatus());
            refreshTimer.start();

            updateStatus();
        }

        private void updateStatus() {
            McpServerManager manager = McpServerManager.getInstance(project);
            boolean running = manager.isRunning();
            boolean phpAvailable = PhpPluginHelper.isPhpPluginAvailable();

            // Status info
            StringBuilder status = new StringBuilder("<html>");
            status.append("<b>Status:</b> ").append(running ?
                "<font color='green'>Running</font>" :
                "<font color='gray'>Stopped</font>").append("<br>");
            if (running) {
                status.append("<b>Port:</b> ").append(manager.getPort()).append("<br>");
                status.append("<b>Tools:</b> ").append(manager.getToolCount()).append("<br>");
            }
            status.append("<b>PHP Support:</b> ").append(phpAvailable ?
                "<font color='green'>Available</font>" :
                "<font color='gray'>Not Available</font>").append("<br>");

            // Check if PsiPhpCodeFixer is available
            boolean codeFixerAvailable = PhpCodeFixerHelper.isPluginAvailable();
            status.append("<b>Code Fixer:</b> ").append(codeFixerAvailable ?
                "<font color='green'>Available</font>" :
                "<font color='gray'>Not Installed</font>");
            status.append("</html>");
            statusLabel.setText(status.toString());

            // Tools list
            StringBuilder tools = new StringBuilder("<html><div style='font-family: monospace; font-size: 11px;'>");
            if (running) {
                List<String> toolNames = manager.getToolNames();
                if (toolNames.isEmpty()) {
                    tools.append("<i>No tools registered</i>");
                } else {
                    // Group tools by type
                    tools.append("<b>Core Tools:</b><br>");
                    for (String name : toolNames) {
                        if (!name.contains("php")) {
                            tools.append("&nbsp;&nbsp;• ").append(name).append("<br>");
                        }
                    }

                    // PHP tools
                    boolean hasPhpTools = toolNames.stream().anyMatch(n -> n.contains("php"));
                    if (hasPhpTools) {
                        tools.append("<br><b>PHP Tools:</b><br>");
                        for (String name : toolNames) {
                            if (name.contains("php")) {
                                tools.append("&nbsp;&nbsp;• ").append(name).append("<br>");
                            }
                        }
                    }
                }
            } else {
                tools.append("<i>Start the server to see available tools</i>");
            }
            tools.append("</div></html>");
            toolsLabel.setText(tools.toString());

            startButton.setEnabled(!running);
            stopButton.setEnabled(running);
        }
    }
}
