package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.project.Project;

/**
 * Base class for all MCP tools.
 * Provides common functionality for tool implementations.
 */
public abstract class BaseTool {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected final Project project;

    protected BaseTool(Project project) {
        this.project = project;
    }

    /**
     * @return The unique name of this tool (e.g., "rename_element")
     */
    public abstract String getName();

    /**
     * @return Human-readable description of what this tool does
     */
    public abstract String getDescription();

    /**
     * @return JSON Schema describing the input parameters
     */
    public abstract ObjectNode getInputSchema();

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments The arguments as a JSON node
     * @return The result of the tool execution
     */
    public abstract ToolResult execute(JsonNode arguments);

    /**
     * Helper to create a success result.
     */
    protected ToolResult success(String message) {
        ObjectNode content = MAPPER.createObjectNode();
        content.put("success", true);
        content.put("message", message);
        return new ToolResult(true, content);
    }

    /**
     * Helper to create a success result with additional data.
     */
    protected ToolResult success(ObjectNode data) {
        data.put("success", true);
        return new ToolResult(true, data);
    }

    /**
     * Helper to create an error result.
     */
    protected ToolResult error(String message) {
        ObjectNode content = MAPPER.createObjectNode();
        content.put("success", false);
        content.put("error", message);
        return new ToolResult(false, content);
    }

    /**
     * Get a required string parameter.
     */
    protected String getRequiredString(JsonNode args, String name) throws IllegalArgumentException {
        JsonNode node = args.get(name);
        if (node == null || node.isNull() || !node.isTextual()) {
            throw new IllegalArgumentException("Missing required parameter: " + name);
        }
        return node.asText();
    }

    /**
     * Get an optional string parameter with a default value.
     */
    protected String getOptionalString(JsonNode args, String name, String defaultValue) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull() || !node.isTextual()) {
            return defaultValue;
        }
        return node.asText();
    }

    /**
     * Get an optional integer parameter with a default value.
     */
    protected int getOptionalInt(JsonNode args, String name, int defaultValue) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull() || !node.isNumber()) {
            return defaultValue;
        }
        return node.asInt();
    }

    /**
     * Get an optional boolean parameter with a default value.
     */
    protected boolean getOptionalBoolean(JsonNode args, String name, boolean defaultValue) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull() || !node.isBoolean()) {
            return defaultValue;
        }
        return node.asBoolean();
    }

    /**
     * Result of a tool execution.
     */
    public record ToolResult(boolean isSuccess, ObjectNode content) {
    }
}
