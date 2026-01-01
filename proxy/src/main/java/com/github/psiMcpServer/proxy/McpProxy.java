package com.github.psiMcpServer.proxy;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;

/**
 * MCP Proxy - Bridges stdio (for Claude Desktop) to the IDE plugin's socket server.
 *
 * Usage: java -jar mcp-proxy.jar [host] [port]
 *   host: The host where the IDE plugin is running (default: localhost)
 *   port: The port the IDE plugin is listening on (default: 3000)
 *
 * This proxy:
 * 1. Reads JSON-RPC messages from stdin (sent by Claude Desktop)
 * 2. Forwards them to the IDE plugin via socket
 * 3. Reads responses from the plugin
 * 4. Writes them to stdout (received by Claude Desktop)
 */
public class McpProxy {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3000;
    private static final int CONNECT_RETRY_DELAY_MS = 1000;
    private static final int MAX_CONNECT_RETRIES = 30;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        McpProxy proxy = new McpProxy();
        proxy.run(host, port);
    }

    public void run(String host, int port) {
        Socket socket = null;

        // Try to connect with retries (the IDE might still be starting)
        for (int i = 0; i < MAX_CONNECT_RETRIES; i++) {
            try {
                socket = new Socket(host, port);
                break;
            } catch (ConnectException e) {
                if (i < MAX_CONNECT_RETRIES - 1) {
                    try {
                        Thread.sleep(CONNECT_RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        System.err.println("Interrupted while waiting to connect");
                        System.exit(1);
                    }
                } else {
                    System.err.println("Failed to connect to IDE plugin at " + host + ":" + port);
                    System.err.println("Make sure the IDE is running and the MCP server is started.");
                    System.exit(1);
                }
            } catch (IOException e) {
                System.err.println("Error connecting to IDE plugin: " + e.getMessage());
                System.exit(1);
            }
        }

        if (socket == null) {
            System.err.println("Failed to establish connection");
            System.exit(1);
        }

        try (
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter stdout = new PrintWriter(new OutputStreamWriter(System.out), true);
            BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter socketOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            // Start a thread to read responses from the plugin and write to stdout
            Thread responseThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = socketIn.readLine()) != null) {
                        stdout.println(line);
                    }
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        System.err.println("Error reading from plugin: " + e.getMessage());
                    }
                }
            }, "MCP-Response-Reader");
            responseThread.setDaemon(true);
            responseThread.start();

            // Main thread: read from stdin and send to plugin
            String line;
            while ((line = stdin.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    socketOut.println(line);
                }
            }

        } catch (IOException e) {
            System.err.println("Error in proxy communication: " + e.getMessage());
            System.exit(1);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
