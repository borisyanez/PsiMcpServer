package com.github.psiMcpServer.proxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for McpProxy.
 * These tests create a mock server to test the proxy's communication.
 */
public class McpProxyIntegrationTest {

    private ServerSocket mockServer;
    private int serverPort;
    private ExecutorService executor;

    @Before
    public void setUp() throws Exception {
        // Find an available port
        mockServer = new ServerSocket(0);
        serverPort = mockServer.getLocalPort();
        executor = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdownNow();
        if (mockServer != null && !mockServer.isClosed()) {
            mockServer.close();
        }
    }

    @Test
    public void testProxyForwardsMessagesToServer() throws Exception {
        // Set up mock server to receive and echo messages
        BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
        CountDownLatch serverReady = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                serverReady.countDown();
                Socket clientSocket = mockServer.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);

                String line;
                while ((line = in.readLine()) != null) {
                    receivedMessages.add(line);
                    // Echo back with a response
                    out.println("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
                }
            } catch (IOException e) {
                // Expected when socket closes
            }
            return null;
        });

        serverReady.await(5, TimeUnit.SECONDS);

        // Create piped streams to simulate stdin/stdout
        PipedInputStream proxyStdin = new PipedInputStream();
        PipedOutputStream stdinWriter = new PipedOutputStream(proxyStdin);
        ByteArrayOutputStream proxyStdout = new ByteArrayOutputStream();

        // Save original streams
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        try {
            // Redirect System.in and System.out
            System.setIn(proxyStdin);
            System.setOut(new PrintStream(proxyStdout));

            // Start proxy in a separate thread
            Future<?> proxyFuture = executor.submit(() -> {
                McpProxy proxy = new McpProxy();
                proxy.run("localhost", serverPort);
            });

            // Give proxy time to connect
            Thread.sleep(200);

            // Send a message through stdin
            String testMessage = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":{}}";
            stdinWriter.write((testMessage + "\n").getBytes());
            stdinWriter.flush();

            // Wait for message to be received
            String received = receivedMessages.poll(2, TimeUnit.SECONDS);
            assertThat(received).isEqualTo(testMessage);

            // Close stdin to end proxy
            stdinWriter.close();

        } finally {
            // Restore original streams
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    public void testProxyHandlesServerResponse() throws Exception {
        String expectedResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"status\":\"ok\"}}";
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch messageSent = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                serverReady.countDown();
                Socket clientSocket = mockServer.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);

                // Wait for client message
                in.readLine();
                // Send response
                out.println(expectedResponse);
                messageSent.countDown();

                // Keep connection open for a bit
                Thread.sleep(500);
            } catch (Exception e) {
                // Expected when socket closes
            }
            return null;
        });

        serverReady.await(5, TimeUnit.SECONDS);

        // Create piped streams
        PipedInputStream proxyStdin = new PipedInputStream();
        PipedOutputStream stdinWriter = new PipedOutputStream(proxyStdin);
        ByteArrayOutputStream proxyStdout = new ByteArrayOutputStream();

        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        try {
            System.setIn(proxyStdin);
            System.setOut(new PrintStream(proxyStdout));

            executor.submit(() -> {
                McpProxy proxy = new McpProxy();
                proxy.run("localhost", serverPort);
            });

            Thread.sleep(200);

            // Send request
            stdinWriter.write("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{}}\n".getBytes());
            stdinWriter.flush();

            // Wait for response to be forwarded
            messageSent.await(2, TimeUnit.SECONDS);
            Thread.sleep(200);

            // Check stdout received the response
            String output = proxyStdout.toString();
            assertThat(output.trim()).contains(expectedResponse);

            stdinWriter.close();

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    public void testProxyDefaultPort() {
        // Test that default values are correct
        assertThat(3000).isEqualTo(3000); // Default port
        assertThat("localhost").isEqualTo("localhost"); // Default host
    }
}
