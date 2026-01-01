package com.github.psiMcpServer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for tool input schema definitions.
 */
public class ToolSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Project mockProject;

    @Before
    public void setUp() {
        mockProject = mock(Project.class);
    }

    @Test
    public void testRenameElementToolSchema() {
        RenameElementTool tool = new RenameElementTool(mockProject);
        ObjectNode schema = tool.getInputSchema();

        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.has("properties")).isTrue();
        assertThat(schema.has("required")).isTrue();

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("file_path")).isTrue();
        assertThat(properties.has("element_name")).isTrue();
        assertThat(properties.has("new_name")).isTrue();
        assertThat(properties.has("offset")).isTrue();

        JsonNode required = schema.get("required");
        assertThat(required.isArray()).isTrue();
        assertThat(required.toString()).contains("file_path");
        assertThat(required.toString()).contains("element_name");
        assertThat(required.toString()).contains("new_name");
    }

    @Test
    public void testMoveElementToolSchema() {
        MoveElementTool tool = new MoveElementTool(mockProject);
        ObjectNode schema = tool.getInputSchema();

        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("source_path")).isTrue();
        assertThat(properties.has("target_directory")).isTrue();

        JsonNode required = schema.get("required");
        assertThat(required.toString()).contains("source_path");
        assertThat(required.toString()).contains("target_directory");
    }

    @Test
    public void testSafeDeleteToolSchema() {
        SafeDeleteTool tool = new SafeDeleteTool(mockProject);
        ObjectNode schema = tool.getInputSchema();

        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("file_path")).isTrue();
        assertThat(properties.has("element_name")).isTrue();
        assertThat(properties.has("check_usages")).isTrue();

        // check_usages should have a default value
        assertThat(properties.get("check_usages").has("default")).isTrue();
    }

    @Test
    public void testFindUsagesToolSchema() {
        FindUsagesTool tool = new FindUsagesTool(mockProject);
        ObjectNode schema = tool.getInputSchema();

        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("file_path")).isTrue();
        assertThat(properties.has("element_name")).isTrue();
        assertThat(properties.has("offset")).isTrue();
        assertThat(properties.has("max_results")).isTrue();

        // max_results should have a default value
        assertThat(properties.get("max_results").get("default").asInt()).isEqualTo(100);
    }

    @Test
    public void testListProjectFilesToolSchema() {
        ListProjectFilesTool tool = new ListProjectFilesTool(mockProject);
        ObjectNode schema = tool.getInputSchema();

        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("directory")).isTrue();
        assertThat(properties.has("pattern")).isTrue();
        assertThat(properties.has("max_results")).isTrue();
        assertThat(properties.has("include_hidden")).isTrue();

        // All parameters are optional
        JsonNode required = schema.get("required");
        assertThat(required.size()).isEqualTo(0);
    }

    @Test
    public void testGetFileContentsToolSchema() {
        GetFileContentsTool tool = new GetFileContentsTool(mockProject);
        ObjectNode schema = tool.getInputSchema();

        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("file_path")).isTrue();
        assertThat(properties.has("start_line")).isTrue();
        assertThat(properties.has("end_line")).isTrue();

        JsonNode required = schema.get("required");
        assertThat(required.toString()).contains("file_path");
    }

    @Test
    public void testGetElementInfoToolSchema() {
        GetElementInfoTool tool = new GetElementInfoTool(mockProject);
        ObjectNode schema = tool.getInputSchema();

        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode properties = schema.get("properties");
        assertThat(properties.has("file_path")).isTrue();
        assertThat(properties.has("element_name")).isTrue();
        assertThat(properties.has("offset")).isTrue();

        JsonNode required = schema.get("required");
        assertThat(required.toString()).contains("file_path");
    }

    @Test
    public void testAllToolsHaveRequiredFields() {
        BaseTool[] tools = {
            new RenameElementTool(mockProject),
            new MoveElementTool(mockProject),
            new SafeDeleteTool(mockProject),
            new FindUsagesTool(mockProject),
            new ListProjectFilesTool(mockProject),
            new GetFileContentsTool(mockProject),
            new GetElementInfoTool(mockProject)
        };

        for (BaseTool tool : tools) {
            assertThat(tool.getName())
                .as("Tool name should not be null or empty")
                .isNotNull()
                .isNotEmpty();

            assertThat(tool.getDescription())
                .as("Tool description should not be null or empty for " + tool.getName())
                .isNotNull()
                .isNotEmpty();

            ObjectNode schema = tool.getInputSchema();
            assertThat(schema)
                .as("Tool schema should not be null for " + tool.getName())
                .isNotNull();

            assertThat(schema.get("type").asText())
                .as("Tool schema type should be 'object' for " + tool.getName())
                .isEqualTo("object");
        }
    }

    @Test
    public void testToolNames() {
        assertThat(new RenameElementTool(mockProject).getName()).isEqualTo("rename_element");
        assertThat(new MoveElementTool(mockProject).getName()).isEqualTo("move_element");
        assertThat(new SafeDeleteTool(mockProject).getName()).isEqualTo("safe_delete");
        assertThat(new FindUsagesTool(mockProject).getName()).isEqualTo("find_usages");
        assertThat(new ListProjectFilesTool(mockProject).getName()).isEqualTo("list_project_files");
        assertThat(new GetFileContentsTool(mockProject).getName()).isEqualTo("get_file_contents");
        assertThat(new GetElementInfoTool(mockProject).getName()).isEqualTo("get_element_info");
    }

    @Test
    public void testPropertyDescriptions() {
        RenameElementTool tool = new RenameElementTool(mockProject);
        ObjectNode schema = tool.getInputSchema();
        JsonNode properties = schema.get("properties");

        // Each property should have a description
        properties.fieldNames().forEachRemaining(fieldName -> {
            JsonNode property = properties.get(fieldName);
            assertThat(property.has("description"))
                .as("Property '%s' should have a description", fieldName)
                .isTrue();
            assertThat(property.get("description").asText())
                .as("Property '%s' description should not be empty", fieldName)
                .isNotEmpty();
        });
    }
}
