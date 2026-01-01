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
 * Unit tests for BaseTool helper methods.
 */
public class BaseToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private TestTool tool;
    private Project mockProject;

    @Before
    public void setUp() {
        mockProject = mock(Project.class);
        tool = new TestTool(mockProject);
    }

    // Test implementation of BaseTool for testing
    private static class TestTool extends BaseTool {
        TestTool(Project project) {
            super(project);
        }

        @Override
        public String getName() {
            return "test_tool";
        }

        @Override
        public String getDescription() {
            return "A test tool";
        }

        @Override
        public ObjectNode getInputSchema() {
            return MAPPER.createObjectNode();
        }

        @Override
        public ToolResult execute(JsonNode arguments) {
            return success("executed");
        }

        // Expose protected methods for testing
        public String testGetRequiredString(JsonNode args, String name) {
            return getRequiredString(args, name);
        }

        public String testGetOptionalString(JsonNode args, String name, String defaultValue) {
            return getOptionalString(args, name, defaultValue);
        }

        public int testGetOptionalInt(JsonNode args, String name, int defaultValue) {
            return getOptionalInt(args, name, defaultValue);
        }

        public boolean testGetOptionalBoolean(JsonNode args, String name, boolean defaultValue) {
            return getOptionalBoolean(args, name, defaultValue);
        }

        public ToolResult testSuccess(String message) {
            return success(message);
        }

        public ToolResult testError(String message) {
            return error(message);
        }
    }

    @Test
    public void testGetRequiredString_withValidValue() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("name", "testValue");

        String result = tool.testGetRequiredString(args, "name");

        assertThat(result).isEqualTo("testValue");
    }

    @Test
    public void testGetRequiredString_withMissingValue_throwsException() {
        ObjectNode args = MAPPER.createObjectNode();

        assertThatThrownBy(() -> tool.testGetRequiredString(args, "name"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required parameter: name");
    }

    @Test
    public void testGetRequiredString_withNullValue_throwsException() {
        ObjectNode args = MAPPER.createObjectNode();
        args.putNull("name");

        assertThatThrownBy(() -> tool.testGetRequiredString(args, "name"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required parameter: name");
    }

    @Test
    public void testGetRequiredString_withNonStringValue_throwsException() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("name", 123);

        assertThatThrownBy(() -> tool.testGetRequiredString(args, "name"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required parameter: name");
    }

    @Test
    public void testGetOptionalString_withValue() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("name", "providedValue");

        String result = tool.testGetOptionalString(args, "name", "defaultValue");

        assertThat(result).isEqualTo("providedValue");
    }

    @Test
    public void testGetOptionalString_withMissingValue_returnsDefault() {
        ObjectNode args = MAPPER.createObjectNode();

        String result = tool.testGetOptionalString(args, "name", "defaultValue");

        assertThat(result).isEqualTo("defaultValue");
    }

    @Test
    public void testGetOptionalString_withNullValue_returnsDefault() {
        ObjectNode args = MAPPER.createObjectNode();
        args.putNull("name");

        String result = tool.testGetOptionalString(args, "name", "defaultValue");

        assertThat(result).isEqualTo("defaultValue");
    }

    @Test
    public void testGetOptionalInt_withValue() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("count", 42);

        int result = tool.testGetOptionalInt(args, "count", 10);

        assertThat(result).isEqualTo(42);
    }

    @Test
    public void testGetOptionalInt_withMissingValue_returnsDefault() {
        ObjectNode args = MAPPER.createObjectNode();

        int result = tool.testGetOptionalInt(args, "count", 10);

        assertThat(result).isEqualTo(10);
    }

    @Test
    public void testGetOptionalInt_withNonNumericValue_returnsDefault() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("count", "not a number");

        int result = tool.testGetOptionalInt(args, "count", 10);

        assertThat(result).isEqualTo(10);
    }

    @Test
    public void testGetOptionalBoolean_withTrueValue() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("enabled", true);

        boolean result = tool.testGetOptionalBoolean(args, "enabled", false);

        assertThat(result).isTrue();
    }

    @Test
    public void testGetOptionalBoolean_withFalseValue() {
        ObjectNode args = MAPPER.createObjectNode();
        args.put("enabled", false);

        boolean result = tool.testGetOptionalBoolean(args, "enabled", true);

        assertThat(result).isFalse();
    }

    @Test
    public void testGetOptionalBoolean_withMissingValue_returnsDefault() {
        ObjectNode args = MAPPER.createObjectNode();

        boolean result = tool.testGetOptionalBoolean(args, "enabled", true);

        assertThat(result).isTrue();
    }

    @Test
    public void testSuccess_createsSuccessResult() {
        BaseTool.ToolResult result = tool.testSuccess("Operation completed");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.content().get("success").asBoolean()).isTrue();
        assertThat(result.content().get("message").asText()).isEqualTo("Operation completed");
    }

    @Test
    public void testError_createsErrorResult() {
        BaseTool.ToolResult result = tool.testError("Something went wrong");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.content().get("success").asBoolean()).isFalse();
        assertThat(result.content().get("error").asText()).isEqualTo("Something went wrong");
    }

    @Test
    public void testToolName() {
        assertThat(tool.getName()).isEqualTo("test_tool");
    }

    @Test
    public void testToolDescription() {
        assertThat(tool.getDescription()).isEqualTo("A test tool");
    }

    @Test
    public void testExecute() {
        ObjectNode args = MAPPER.createObjectNode();

        BaseTool.ToolResult result = tool.execute(args);

        assertThat(result.isSuccess()).isTrue();
    }
}
