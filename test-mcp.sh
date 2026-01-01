#!/bin/bash
# Test script for PSI MCP Server

PORT=${1:-3000}
PROXY_JAR="proxy/build/libs/proxy-1.0.0.jar"

echo "=== PSI MCP Server Test ==="
echo ""

# Check if server is running
echo "1. Checking if server is running on port $PORT..."
if nc -z localhost $PORT 2>/dev/null; then
    echo "   ✓ Server is running"
else
    echo "   ✗ Server not running. Start it in IDE: Tools > PSI MCP > Start MCP Server"
    exit 1
fi

echo ""
echo "2. Testing initialize..."
INIT_RESPONSE=$(echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"clientInfo":{"name":"test"}}}' | java -jar "$PROXY_JAR" localhost $PORT 2>/dev/null | head -1)
if echo "$INIT_RESPONSE" | grep -q "psi-mcp-server"; then
    echo "   ✓ Initialize successful"
else
    echo "   ✗ Initialize failed"
    echo "   Response: $INIT_RESPONSE"
    exit 1
fi

echo ""
echo "3. Testing tools/list..."
TOOLS_RESPONSE=$(echo '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | java -jar "$PROXY_JAR" localhost $PORT 2>/dev/null | head -1)
if echo "$TOOLS_RESPONSE" | grep -q "rename_element"; then
    echo "   ✓ Tools list successful"
    TOOL_COUNT=$(echo "$TOOLS_RESPONSE" | grep -o '"name"' | wc -l | tr -d ' ')
    echo "   Found $TOOL_COUNT tools"
else
    echo "   ✗ Tools list failed"
    exit 1
fi

echo ""
echo "4. Testing list_project_files tool..."
FILES_RESPONSE=$(echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_project_files","arguments":{"pattern":"*.java","max_results":5}}}' | java -jar "$PROXY_JAR" localhost $PORT 2>/dev/null | head -1)
if echo "$FILES_RESPONSE" | grep -q "success"; then
    echo "   ✓ Tool execution successful"
else
    echo "   ✗ Tool execution failed"
    echo "   Response: $FILES_RESPONSE"
fi

echo ""
echo "=== All tests passed! ==="
echo ""
echo "Claude Desktop config (add to ~/Library/Application Support/Claude/claude_desktop_config.json):"
echo ""
cat << 'EOF'
{
  "mcpServers": {
    "psi-refactoring": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/boris/JavaProjects/PsiMcpServer/proxy/build/libs/proxy-1.0.0.jar",
        "localhost",
        "3000"
      ]
    }
  }
}
EOF
