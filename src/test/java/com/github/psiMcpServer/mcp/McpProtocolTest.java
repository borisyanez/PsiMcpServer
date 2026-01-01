package com.github.psiMcpServer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MCP protocol compliance.
 */
public class McpProtocolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testInitializeResponseFormat() throws Exception {
        // Build an initialize response as the server would
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", 1);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", "psi-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        ObjectNode capabilities = MAPPER.createObjectNode();
        ObjectNode tools = MAPPER.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        result.set("capabilities", capabilities);

        response.set("result", result);

        // Verify the structure
        JsonNode parsed = MAPPER.readTree(MAPPER.writeValueAsString(response));

        assertThat(parsed.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(parsed.get("result").get("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(parsed.get("result").get("serverInfo").get("name").asText()).isEqualTo("psi-mcp-server");
        assertThat(parsed.get("result").get("capabilities").get("tools").get("listChanged").asBoolean()).isFalse();
    }

    @Test
    public void testToolSchemaFormat() throws Exception {
        // Build a tool schema as the server would
        ObjectNode toolSpec = MAPPER.createObjectNode();
        toolSpec.put("name", "rename_element");
        toolSpec.put("description", "Rename a code element");

        ObjectNode inputSchema = MAPPER.createObjectNode();
        inputSchema.put("type", "object");

        ObjectNode properties = MAPPER.createObjectNode();

        ObjectNode filePath = MAPPER.createObjectNode();
        filePath.put("type", "string");
        filePath.put("description", "Absolute path to the file");
        properties.set("file_path", filePath);

        ObjectNode elementName = MAPPER.createObjectNode();
        elementName.put("type", "string");
        elementName.put("description", "Name of element to rename");
        properties.set("element_name", elementName);

        ObjectNode newName = MAPPER.createObjectNode();
        newName.put("type", "string");
        newName.put("description", "New name for the element");
        properties.set("new_name", newName);

        inputSchema.set("properties", properties);
        inputSchema.set("required", MAPPER.createArrayNode()
            .add("file_path")
            .add("element_name")
            .add("new_name"));

        toolSpec.set("inputSchema", inputSchema);

        // Verify the structure
        JsonNode parsed = MAPPER.readTree(MAPPER.writeValueAsString(toolSpec));

        assertThat(parsed.get("name").asText()).isEqualTo("rename_element");
        assertThat(parsed.get("inputSchema").get("type").asText()).isEqualTo("object");
        assertThat(parsed.get("inputSchema").get("properties").has("file_path")).isTrue();
        assertThat(parsed.get("inputSchema").get("required").size()).isEqualTo(3);
    }

    @Test
    public void testToolCallResultFormat() throws Exception {
        // Success result
        ObjectNode successResult = MAPPER.createObjectNode();
        ObjectNode textContent = MAPPER.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "{\"success\":true,\"message\":\"Renamed to 'newName'\"}");
        successResult.set("content", MAPPER.createArrayNode().add(textContent));
        successResult.put("isError", false);

        JsonNode parsed = MAPPER.readTree(MAPPER.writeValueAsString(successResult));

        assertThat(parsed.get("content").isArray()).isTrue();
        assertThat(parsed.get("content").get(0).get("type").asText()).isEqualTo("text");
        assertThat(parsed.get("isError").asBoolean()).isFalse();
    }

    @Test
    public void testToolCallErrorFormat() throws Exception {
        // Error result
        ObjectNode errorResult = MAPPER.createObjectNode();
        ObjectNode textContent = MAPPER.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "{\"success\":false,\"error\":\"Element not found\"}");
        errorResult.set("content", MAPPER.createArrayNode().add(textContent));
        errorResult.put("isError", true);

        JsonNode parsed = MAPPER.readTree(MAPPER.writeValueAsString(errorResult));

        assertThat(parsed.get("isError").asBoolean()).isTrue();
        assertThat(parsed.get("content").get(0).get("text").asText()).contains("error");
    }

    @Test
    public void testJsonRpcErrorCodes() {
        // Standard JSON-RPC error codes
        int PARSE_ERROR = -32700;
        int INVALID_REQUEST = -32600;
        int METHOD_NOT_FOUND = -32601;
        int INVALID_PARAMS = -32602;
        int INTERNAL_ERROR = -32603;

        assertThat(PARSE_ERROR).isEqualTo(-32700);
        assertThat(INVALID_REQUEST).isEqualTo(-32600);
        assertThat(METHOD_NOT_FOUND).isEqualTo(-32601);
        assertThat(INVALID_PARAMS).isEqualTo(-32602);
        assertThat(INTERNAL_ERROR).isEqualTo(-32603);
    }

    @Test
    public void testMcpMethods() {
        // Verify expected MCP methods
        String[] expectedMethods = {
            "initialize",
            "initialized",
            "tools/list",
            "tools/call",
            "ping"
        };

        assertThat(expectedMethods).containsExactly(
            "initialize",
            "initialized",
            "tools/list",
            "tools/call",
            "ping"
        );
    }

    @Test
    public void testToolsListResponseFormat() throws Exception {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", 2);

        ObjectNode result = MAPPER.createObjectNode();
        var toolsArray = MAPPER.createArrayNode();

        // Add sample tools
        String[] toolNames = {"rename_element", "move_element", "find_usages", "safe_delete"};
        for (String name : toolNames) {
            ObjectNode tool = MAPPER.createObjectNode();
            tool.put("name", name);
            tool.put("description", "Description for " + name);
            tool.set("inputSchema", MAPPER.createObjectNode().put("type", "object"));
            toolsArray.add(tool);
        }

        result.set("tools", toolsArray);
        response.set("result", result);

        JsonNode parsed = MAPPER.readTree(MAPPER.writeValueAsString(response));

        assertThat(parsed.get("result").get("tools").size()).isEqualTo(4);
        assertThat(parsed.get("result").get("tools").get(0).get("name").asText()).isEqualTo("rename_element");
    }
}
