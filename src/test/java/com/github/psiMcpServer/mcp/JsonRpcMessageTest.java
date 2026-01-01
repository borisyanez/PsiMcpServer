package com.github.psiMcpServer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for JSON-RPC message parsing and validation.
 */
public class JsonRpcMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testParseValidRequest() throws Exception {
        String json = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {"clientInfo": {"name": "test"}}
            }
            """;

        JsonNode request = MAPPER.readTree(json);

        assertThat(request.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(request.get("id").asInt()).isEqualTo(1);
        assertThat(request.get("method").asText()).isEqualTo("initialize");
        assertThat(request.has("params")).isTrue();
        assertThat(request.get("params").get("clientInfo").get("name").asText()).isEqualTo("test");
    }

    @Test
    public void testParseNotification() throws Exception {
        String json = """
            {
                "jsonrpc": "2.0",
                "method": "initialized",
                "params": {}
            }
            """;

        JsonNode request = MAPPER.readTree(json);

        assertThat(request.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(request.has("id")).isFalse();
        assertThat(request.get("method").asText()).isEqualTo("initialized");
    }

    @Test
    public void testParseToolsCallRequest() throws Exception {
        String json = """
            {
                "jsonrpc": "2.0",
                "id": 5,
                "method": "tools/call",
                "params": {
                    "name": "rename_element",
                    "arguments": {
                        "file_path": "/path/to/file.java",
                        "element_name": "oldName",
                        "new_name": "newName"
                    }
                }
            }
            """;

        JsonNode request = MAPPER.readTree(json);
        JsonNode params = request.get("params");

        assertThat(request.get("method").asText()).isEqualTo("tools/call");
        assertThat(params.get("name").asText()).isEqualTo("rename_element");
        assertThat(params.get("arguments").get("file_path").asText()).isEqualTo("/path/to/file.java");
        assertThat(params.get("arguments").get("element_name").asText()).isEqualTo("oldName");
        assertThat(params.get("arguments").get("new_name").asText()).isEqualTo("newName");
    }

    @Test
    public void testBuildSuccessResponse() throws Exception {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", 1);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", "psi-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);
        response.set("result", result);

        String json = MAPPER.writeValueAsString(response);

        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"id\":1");
        assertThat(json).contains("\"protocolVersion\":\"2024-11-05\"");
        assertThat(json).contains("\"psi-mcp-server\"");
    }

    @Test
    public void testBuildErrorResponse() throws Exception {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", 1);

        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", -32600);
        error.put("message", "Invalid Request");
        response.set("error", error);

        String json = MAPPER.writeValueAsString(response);

        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"error\"");
        assertThat(json).contains("\"code\":-32600");
        assertThat(json).contains("\"message\":\"Invalid Request\"");
    }

    @Test
    public void testBuildToolCallResult() throws Exception {
        ObjectNode result = MAPPER.createObjectNode();

        ObjectNode textContent = MAPPER.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "{\"success\":true,\"message\":\"Renamed successfully\"}");

        result.set("content", MAPPER.createArrayNode().add(textContent));
        result.put("isError", false);

        String json = MAPPER.writeValueAsString(result);

        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"isError\":false");
        assertThat(json).contains("success");
    }

    @Test
    public void testParseStringId() throws Exception {
        String json = """
            {
                "jsonrpc": "2.0",
                "id": "request-123",
                "method": "ping",
                "params": {}
            }
            """;

        JsonNode request = MAPPER.readTree(json);

        assertThat(request.get("id").asText()).isEqualTo("request-123");
    }

    @Test
    public void testParseNullId() throws Exception {
        String json = """
            {
                "jsonrpc": "2.0",
                "id": null,
                "method": "ping",
                "params": {}
            }
            """;

        JsonNode request = MAPPER.readTree(json);

        assertThat(request.get("id").isNull()).isTrue();
    }

    @Test
    public void testToolsListResponse() throws Exception {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", 2);

        ObjectNode result = MAPPER.createObjectNode();
        var toolsArray = MAPPER.createArrayNode();

        ObjectNode tool1 = MAPPER.createObjectNode();
        tool1.put("name", "rename_element");
        tool1.put("description", "Rename a code element");

        ObjectNode inputSchema = MAPPER.createObjectNode();
        inputSchema.put("type", "object");
        tool1.set("inputSchema", inputSchema);

        toolsArray.add(tool1);
        result.set("tools", toolsArray);
        response.set("result", result);

        String json = MAPPER.writeValueAsString(response);

        assertThat(json).contains("\"tools\"");
        assertThat(json).contains("\"rename_element\"");
        assertThat(json).contains("\"inputSchema\"");
    }
}
