# PSI MCP Server

An IntelliJ IDEA plugin that exposes PSI (Program Structure Interface) refactoring capabilities via MCP (Model Context Protocol) for integration with Claude Desktop and other MCP-compatible clients.

## Features

- Socket-based MCP server running inside the IDE
- Standalone proxy for Claude Desktop stdio integration
- 7 refactoring and code navigation tools
- Configurable port via IDE settings
- Works with all JetBrains IDEs (IntelliJ IDEA, PHPStorm, WebStorm, etc.)

## Available Tools

### Core Tools (All IDEs)

| Tool | Description |
|------|-------------|
| `rename_element` | Rename classes, methods, variables, or files |
| `move_element` | Move files to a different directory |
| `safe_delete` | Delete files/elements with usage checking |
| `find_usages` | Find all references to an element |
| `list_project_files` | List project files with glob pattern filtering |
| `get_file_contents` | Read file contents with optional line range |
| `get_element_info` | Get PSI element metadata (type, modifiers, etc.) |

### PHP-Specific Tools (PHPStorm only)

| Tool | Description |
|------|-------------|
| `move_php_class` | Move a PHP class to a new namespace/directory with full reference updates |

The `move_php_class` tool provides enhanced PHP class moving capabilities:
- Updates the namespace declaration in the moved file
- Updates all `use` statements across the project
- Updates class references (extends, implements, type hints)
- Supports PSR-4 namespace auto-detection from directory structure

## Building

### Prerequisites

- Java 21 or later
- Gradle 8.11 or later (wrapper included)

### Build the plugin

```bash
./gradlew buildPlugin
```

The plugin ZIP will be at `build/distributions/PsiMcpServer-1.0.0.zip`

### Build the proxy

```bash
./gradlew :proxy:jar
```

The proxy JAR will be at `proxy/build/libs/proxy-1.0.0.jar`

## Installation

### Install the plugin

1. Open your JetBrains IDE
2. Go to **Settings** → **Plugins** → **⚙️** → **Install Plugin from Disk...**
3. Select `build/distributions/PsiMcpServer-1.0.0.zip`
4. Restart the IDE

### Configure the port (optional)

1. Go to **Settings** → **Tools** → **PSI MCP Server**
2. Set the desired port (default: 3000)
3. Enable/disable auto-start on IDE launch

## Claude Desktop Integration

### 1. Copy the proxy JAR

```bash
cp proxy/build/libs/proxy-1.0.0.jar ~/Library/Application\ Support/Claude/mcp-proxy.jar
```

### 2. Configure Claude Desktop

Edit `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "psi-mcp-server": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/YOUR_USERNAME/Library/Application Support/Claude/mcp-proxy.jar",
        "localhost",
        "3000"
      ]
    }
  }
}
```

Replace `YOUR_USERNAME` with your actual username.

### 3. Start the MCP server

In your IDE:
- Go to **Tools** → **Start MCP Server**, or
- Use the MCP Server tool window (View → Tool Windows → MCP Server)

### 4. Restart Claude Desktop

The PSI MCP tools should now be available in Claude Desktop.

## Usage Examples

Once connected, you can ask Claude to:

- "Rename the class `UserService` to `UserManager`"
- "Find all usages of the `processData` method"
- "Move `Utils.java` to the `helpers` package"
- "Show me the contents of `src/main/java/App.java`"
- "List all Java files in the project"

### PHP-Specific Examples (PHPStorm)

- "Move the `UserController` class to the `App\Http\Controllers\Api` namespace"
- "Refactor `Services/PaymentService.php` to `Services/Payment/StripeService.php`"

## Testing

### Run unit tests

```bash
./gradlew test
```

### Manual testing

Use the included test script to verify the MCP server:

```bash
# Start the server in IDE first, then:
./test-mcp.sh
```

Or test manually with netcat:

```bash
# Initialize
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"clientInfo":{"name":"test"}}}' | nc localhost 3000

# List tools
echo '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' | nc localhost 3000
```

## Project Structure

```
PsiMcpServer/
├── src/main/java/com/github/psiMcpServer/
│   ├── mcp/                 # MCP server implementation
│   │   ├── McpServerManager.java
│   │   ├── McpSocketServer.java
│   │   └── ToolRegistry.java
│   ├── tools/               # MCP tool implementations
│   │   ├── BaseTool.java
│   │   ├── RenameElementTool.java
│   │   ├── MoveElementTool.java
│   │   └── ...
│   ├── psi/                 # PSI utilities
│   │   ├── PsiElementResolver.java
│   │   └── RefactoringExecutor.java
│   └── settings/            # Plugin settings
├── proxy/                   # Standalone MCP proxy for Claude Desktop
└── src/test/                # Unit tests
```

## License

MIT
