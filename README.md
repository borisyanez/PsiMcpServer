# PSI MCP Server

An IntelliJ IDEA plugin that exposes PSI (Program Structure Interface) refactoring capabilities via MCP (Model Context Protocol) for integration with Claude Desktop and other MCP-compatible clients.

## Features

- Socket-based MCP server running inside the IDE
- Standalone proxy for Claude Desktop stdio integration
- 7 core refactoring and code navigation tools + 2 PHP-specific tools
- Configurable port via IDE settings
- Works with all JetBrains IDEs (IntelliJ IDEA, PHPStorm, WebStorm, etc.)
- Tool window showing server status, available tools, and PHP support indicator
- Background task progress reporting in IDE status bar for long-running operations

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
| `batch_move_php_classes` | Move multiple PHP classes at once (entire directories or by pattern) |

**`move_php_class`** - Single class move with:
- Updates the namespace declaration in the moved file
- Updates `use` statements and class references **inside** the moved file (for sibling class references)
- Updates all `use` statements across the project (external references)
- Updates class references (extends, implements, type hints) in other files
- Supports PSR-4 namespace auto-detection from directory structure
- Automatically adds `use` statements for classes that were in the same namespace
- Prefixes global namespace class references with `\` when moving from global to named namespace

**`batch_move_php_classes`** - Batch operations for:
- Moving all PHP files from one directory to another
- Moving files matching a glob pattern (e.g., `*Controller.php`, `Service*.php`)
- Recursive subdirectory processing
- Preserving directory structure during moves
- Bulk namespace and reference updates

### How PHP Class Moving Works

When you move a PHP class, the plugin performs a complete refactoring:

#### 1. Internal References (inside the moved file)
Classes that were in the same namespace and referenced by short name get proper `use` statements added:

```php
// BEFORE: App\Services\UserService.php
namespace App\Services;

class UserService {
    public function __construct(
        private Helper $helper,           // Same namespace - no use needed
        private \App\Models\User $user    // FQN - stays the same
    ) {}
}

// AFTER: App\Domain\Services\UserService.php
namespace App\Domain\Services;

use App\Services\Helper;                  // ← Auto-added!

class UserService {
    public function __construct(
        private Helper $helper,           // Now resolved via use statement
        private \App\Models\User $user    // FQN - unchanged
    ) {}
}
```

#### 2. External References (other files)
All files that reference the moved class are updated:

```php
// BEFORE: Some other file
use App\Services\UserService;

// AFTER: Automatically updated
use App\Domain\Services\UserService;
```

#### 3. Class References
Extends, implements, type hints, and other class references are updated throughout the project.

#### 4. Require/Include Statements
Relative paths in `require`, `include`, `require_once`, and `include_once` statements are adjusted:

```php
// BEFORE: App\Services\UserService.php
require_once __DIR__ . '/../Models/User.php';
require_once __DIR__ . '/Helper.php';

// AFTER: App\Domain\Services\UserService.php (moved 1 level deeper)
require_once __DIR__ . '/../../Models/User.php';  // ← Extra ../ added
require_once __DIR__ . '/../Helper.php';          // ← Extra ../ added
```

#### 5. Global Namespace References
When moving a class from the global namespace to a named namespace, references to other global namespace classes are prefixed with `\`:

```php
// BEFORE: MyClass.php (global namespace)
class MyClass {
    public function foo() {
        $logger = new GlobalLogger();    // Resolves to \GlobalLogger
        $helper = new SomeHelper();      // Resolves to \SomeHelper
    }
}

// AFTER: App\Services\MyClass.php (moved to namespace)
namespace App\Services;

class MyClass {
    public function foo() {
        $logger = new \GlobalLogger();   // ← Backslash added
        $helper = new \SomeHelper();     // ← Backslash added
    }
}
```

This ensures that references to global namespace classes continue to resolve correctly after the move, since unqualified class names in a namespace would otherwise try to resolve within that namespace.

#### 6. Progress Reporting
PHP class move operations display progress in the IDE status bar:

**Single class move (`move_php_class`):**
- Shows "Moving PHP Class: ClassName" as the task title
- Reports current stage: Finding class, Collecting references, Moving file, Updating namespace, Updating internal/external references

**Batch move (`batch_move_php_classes`):**
- Shows "Moving PHP Classes" or "Moving PHP Classes: pattern" as the task title
- Displays progress as percentage with file count (e.g., "Moving PHP classes (3/10)")
- Shows current file name being processed
- Supports cancellation for long-running batch operations

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
- Go to **Tools** → **PSI MCP** → **Start PSI MCP Server**, or
- Use the PSI MCP tool window (View → Tool Windows → PSI MCP)

The tool window displays:
- Server status (Running/Stopped) and port
- PHP Support availability (for PHPStorm)
- List of all available tools grouped by category

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
- "Move all controllers from `app/Http/Controllers` to `app/Http/Controllers/V2`"
- "Move all `*Repository.php` files to the `Repositories` folder"
- "Batch move all services matching `*Service.php` to the new `Domain/Services` namespace"

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
│   │   ├── MovePhpClassTool.java
│   │   ├── BatchMovePhpClassesTool.java
│   │   └── ...
│   ├── php/                 # PHP-specific handlers (PHPStorm)
│   │   ├── PhpMoveHandler.java
│   │   ├── PhpBatchMoveHandler.java
│   │   ├── ManualReferenceUpdater.java
│   │   └── PhpPluginHelper.java
│   ├── psi/                 # PSI utilities
│   │   ├── PsiElementResolver.java
│   │   └── RefactoringExecutor.java
│   └── settings/            # Plugin settings
├── proxy/                   # Standalone MCP proxy for Claude Desktop
└── src/test/                # Unit tests
```

## License

MIT
