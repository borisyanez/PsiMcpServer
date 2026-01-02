package com.github.psiMcpServer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.tools.BaseTool;
import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ToolRegistry.
 */
public class ToolRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Project mockProject;
    private ToolRegistry registry;

    @Before
    public void setUp() {
        mockProject = mock(Project.class);
        registry = new ToolRegistry(mockProject);
    }

    @Test
    public void testToolCount() {
        // Should have at least 7 base tools registered
        // May have 8 if PHP plugin is available (adds move_php_class)
        assertThat(registry.getToolCount()).isGreaterThanOrEqualTo(7);
    }

    @Test
    public void testHasToolForAllRegisteredTools() {
        // Base tools that are always registered
        String[] baseTools = {
            "rename_element",
            "move_element",
            "safe_delete",
            "find_usages",
            "list_project_files",
            "get_file_contents",
            "get_element_info"
        };

        for (String toolName : baseTools) {
            assertThat(registry.hasTool(toolName))
                .as("Tool '%s' should be registered", toolName)
                .isTrue();
        }
    }

    @Test
    public void testHasToolReturnsFalseForUnknownTool() {
        assertThat(registry.hasTool("unknown_tool")).isFalse();
        assertThat(registry.hasTool("")).isFalse();
        assertThat(registry.hasTool("RENAME_ELEMENT")).isFalse(); // case sensitive
    }

    @Test
    public void testGetToolSpecs() {
        ArrayNode specs = registry.getToolSpecs();

        // At least 7 base tools, may have more if PHP plugin is available
        assertThat(specs.size()).isGreaterThanOrEqualTo(7);

        // Each tool spec should have name, description, and inputSchema
        for (JsonNode spec : specs) {
            assertThat(spec.has("name")).isTrue();
            assertThat(spec.has("description")).isTrue();
            assertThat(spec.has("inputSchema")).isTrue();
            assertThat(spec.get("inputSchema").get("type").asText()).isEqualTo("object");
        }
    }

    @Test
    public void testGetToolSpecsContainsExpectedTools() {
        ArrayNode specs = registry.getToolSpecs();

        // Collect all tool names
        java.util.Set<String> toolNames = new java.util.HashSet<>();
        for (JsonNode spec : specs) {
            toolNames.add(spec.get("name").asText());
        }

        // Base tools that should always be present
        String[] baseTools = {
            "rename_element",
            "move_element",
            "safe_delete",
            "find_usages",
            "list_project_files",
            "get_file_contents",
            "get_element_info"
        };

        for (String toolName : baseTools) {
            assertThat(toolNames)
                .as("Should contain tool '%s'", toolName)
                .contains(toolName);
        }
    }

    @Test
    public void testExecuteToolWithUnknownTool() {
        ObjectNode args = MAPPER.createObjectNode();

        BaseTool.ToolResult result = registry.executeTool("nonexistent_tool", args);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.content().get("success").asBoolean()).isFalse();
        assertThat(result.content().get("error").asText()).contains("Unknown tool");
        assertThat(result.content().get("error").asText()).contains("nonexistent_tool");
    }

    @Test
    public void testToolSpecsHaveDescriptions() {
        ArrayNode specs = registry.getToolSpecs();

        for (JsonNode spec : specs) {
            String name = spec.get("name").asText();
            String description = spec.get("description").asText();

            assertThat(description)
                .as("Tool '%s' should have non-empty description", name)
                .isNotNull()
                .isNotEmpty();
        }
    }

    @Test
    public void testToolSpecsHaveValidInputSchemas() {
        ArrayNode specs = registry.getToolSpecs();

        for (JsonNode spec : specs) {
            String name = spec.get("name").asText();
            JsonNode inputSchema = spec.get("inputSchema");

            assertThat(inputSchema.get("type").asText())
                .as("Tool '%s' should have object type schema", name)
                .isEqualTo("object");

            assertThat(inputSchema.has("properties"))
                .as("Tool '%s' should have properties in schema", name)
                .isTrue();

            assertThat(inputSchema.has("required"))
                .as("Tool '%s' should have required array in schema", name)
                .isTrue();
        }
    }
}
