package com.github.psiMcpServer.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.psiMcpServer.tools.BaseTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Socket-based MCP server that external processes (like the MCP proxy) can connect to.
 * This allows Claude Desktop to communicate with the IDE plugin.
 */
public class McpSocketServer {

    private static final Logger LOG = Logger.getInstance(McpSocketServer.class);
    private static final String JSONRPC_VERSION = "2.0";
    private static final String SERVER_NAME = "psi-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";

    private final Project project;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private ToolRegistry toolRegistry;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private int port;

    public McpSocketServer(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Start the socket server on the specified port.
     */
    public void start(int port) throws IOException {
        if (running.compareAndSet(false, true)) {
            this.port = port;
            toolRegistry = new ToolRegistry(project);
            serverSocket = new ServerSocket(port);
            executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "MCP-Socket-Server");
                t.setDaemon(true);
                return t;
            });

            executor.submit(this::acceptConnections);
            LOG.info("MCP Socket Server started on port " + port + " for project: " + project.getName());
        }
    }

    /**
     * Stop the socket server.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                LOG.warn("Error closing server socket", e);
            }

            if (executor != null) {
                executor.shutdownNow();
            }

            initialized.set(false);
            LOG.info("MCP Socket Server stopped");
        }
    }

    /**
     * Accept incoming connections.
     */
    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOG.info("New MCP client connected from: " + clientSocket.getRemoteSocketAddress());
                executor.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                if (running.get()) {
                    LOG.error("Socket error while accepting connections", e);
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error accepting connection", e);
                }
            }
        }
    }

    /**
     * Handle a connected client.
     */
    private void handleClient(Socket clientSocket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true)
        ) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String response = handleMessage(line);
                if (response != null) {
                    writer.println(response);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                LOG.debug("Client disconnected: " + e.getMessage());
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    /**
     * Handle an incoming JSON-RPC message.
     */
    private String handleMessage(String jsonMessage) {
        try {
            JsonNode request = mapper.readTree(jsonMessage);
            String method = request.has("method") ? request.get("method").asText() : null;
            JsonNode params = request.get("params");
            JsonNode id = request.get("id");

            if (method == null) {
                return createErrorResponse(id, -32600, "Invalid Request: missing method");
            }

            LOG.debug("Received method: " + method);

            Object result = dispatchMethod(method, params);

            // Notifications (no id) don't get a response
            if (id == null || id.isNull()) {
                return null;
            }

            return createResponse(id, result);

        } catch (JsonProcessingException e) {
            LOG.error("JSON parse error", e);
            return createErrorResponse(null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error handling message", e);
            return createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Dispatch a method call to the appropriate handler.
     */
    private Object dispatchMethod(String method, JsonNode params) {
        return switch (method) {
            case "initialize" -> handleInitialize(params);
            case "initialized" -> {
                handleInitialized();
                yield null;
            }
            case "tools/list" -> handleToolsList();
            case "tools/call" -> handleToolsCall(params);
            case "ping" -> handlePing();
            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }

    private ObjectNode handleInitialize(JsonNode params) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.set("serverInfo", serverInfo);

        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode tools = mapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        result.set("capabilities", capabilities);

        return result;
    }

    private void handleInitialized() {
        initialized.set(true);
        LOG.info("MCP client initialized");
    }

    private ObjectNode handleToolsList() {
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", toolRegistry.getToolSpecs());
        return result;
    }

    private ObjectNode handleToolsCall(JsonNode params) {
        String toolName = params.has("name") ? params.get("name").asText() : null;
        JsonNode arguments = params.get("arguments");

        if (toolName == null) {
            ObjectNode result = mapper.createObjectNode();
            result.set("content", mapper.createArrayNode().add(
                mapper.createObjectNode()
                    .put("type", "text")
                    .put("text", "Error: Missing tool name")
            ));
            result.put("isError", true);
            return result;
        }

        BaseTool.ToolResult toolResult = toolRegistry.executeTool(toolName, arguments);

        ObjectNode result = mapper.createObjectNode();
        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        try {
            textContent.put("text", mapper.writeValueAsString(toolResult.content()));
        } catch (JsonProcessingException e) {
            textContent.put("text", toolResult.content().toString());
        }
        result.set("content", mapper.createArrayNode().add(textContent));
        result.put("isError", !toolResult.isSuccess());

        return result;
    }

    private ObjectNode handlePing() {
        return mapper.createObjectNode();
    }

    private String createResponse(JsonNode id, Object result) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", JSONRPC_VERSION);
            response.set("id", id);
            response.set("result", mapper.valueToTree(result));
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            LOG.error("Error creating response", e);
            return createErrorResponse(id, -32603, "Error creating response");
        }
    }

    private String createErrorResponse(JsonNode id, int code, String message) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", JSONRPC_VERSION);
            if (id != null) {
                response.set("id", id);
            } else {
                response.putNull("id");
            }
            ObjectNode error = mapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            response.set("error", error);
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            LOG.error("Error creating error response", e);
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    public int getToolCount() {
        return toolRegistry != null ? toolRegistry.getToolCount() : 0;
    }
}
