package com.github.psiMcpServer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.php.PhpPluginHelper;
import com.github.psiMcpServer.tools.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for all available MCP tools.
 * Handles tool registration, discovery, and dispatch.
 */
public class ToolRegistry {

    private static final Logger LOG = Logger.getInstance(ToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, BaseTool> tools = new LinkedHashMap<>();
    private final Project project;

    public ToolRegistry(Project project) {
        this.project = project;
        registerAllTools();
    }

    /**
     * Register all available tools.
     */
    private void registerAllTools() {
        // Refactoring tools
        register(new RenameElementTool(project));
        register(new MoveElementTool(project));
        register(new SafeDeleteTool(project));
        register(new FindUsagesTool(project));

        // File/content tools
        register(new ListProjectFilesTool(project));
        register(new GetFileContentsTool(project));
        register(new GetElementInfoTool(project));

        // PHP-specific tools (only when PHP plugin is available)
        registerPhpTools();

        LOG.info("Registered " + tools.size() + " MCP tools");
    }

    /**
     * Register PHP-specific tools if the PHP plugin is available.
     */
    private void registerPhpTools() {
        if (PhpPluginHelper.isPhpPluginAvailable()) {
            try {
                register(new MovePhpClassTool(project));
                LOG.info("PHP plugin detected - registered PHP-specific tools");
            } catch (Exception e) {
                LOG.warn("Failed to register PHP tools: " + e.getMessage());
            }
        }
    }

    /**
     * Register a single tool.
     */
    private void register(BaseTool tool) {
        tools.put(tool.getName(), tool);
        LOG.debug("Registered tool: " + tool.getName());
    }

    /**
     * Get all tool specifications for the tools/list response.
     */
    public ArrayNode getToolSpecs() {
        ArrayNode toolsArray = MAPPER.createArrayNode();

        for (BaseTool tool : tools.values()) {
            ObjectNode toolSpec = MAPPER.createObjectNode();
            toolSpec.put("name", tool.getName());
            toolSpec.put("description", tool.getDescription());
            toolSpec.set("inputSchema", tool.getInputSchema());
            toolsArray.add(toolSpec);
        }

        return toolsArray;
    }

    /**
     * Execute a tool by name.
     *
     * @param toolName The name of the tool to execute
     * @param arguments The arguments for the tool
     * @return The result of the tool execution
     */
    public BaseTool.ToolResult executeTool(String toolName, JsonNode arguments) {
        BaseTool tool = tools.get(toolName);
        if (tool == null) {
            ObjectNode errorContent = MAPPER.createObjectNode();
            errorContent.put("success", false);
            errorContent.put("error", "Unknown tool: " + toolName);
            return new BaseTool.ToolResult(false, errorContent);
        }

        try {
            LOG.info("Executing tool: " + toolName);
            return tool.execute(arguments);
        } catch (Exception e) {
            LOG.error("Error executing tool " + toolName, e);
            ObjectNode errorContent = MAPPER.createObjectNode();
            errorContent.put("success", false);
            errorContent.put("error", "Tool execution failed: " + e.getMessage());
            return new BaseTool.ToolResult(false, errorContent);
        }
    }

    /**
     * Check if a tool exists.
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * Get the number of registered tools.
     */
    public int getToolCount() {
        return tools.size();
    }
}
