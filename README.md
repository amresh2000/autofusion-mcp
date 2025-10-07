# Autofusion-MCP Server

A Model Context Protocol (MCP) server that provides unified file comparison capabilities through your Autofusion Core CLI. This server exposes a single tool `autofusion_compare` that intelligently routes between Excel, CSV, and Table comparison modes based on input patterns.

## Features

- **Single Unified Tool**: One `autofusion_compare` tool handles all comparison types
- **Intelligent Routing**: Automatically detects Excel, CSV, or Table comparison based on inputs
- **Natural Language Processing**: Extracts parameters from human-readable prompts
- **Dry-Run Workflow**: Validates inputs before execution with user confirmation
- **Java CLI Integration**: Calls your existing Autofusion Core shaded JAR

## Prerequisites

- Node.js 18+
- Java runtime environment
- Your Autofusion Core shaded JAR file (`autofusion-1.0.0-shaded.jar`)

## Installation & Setup

### 1. Clone and Install Dependencies

```bash
cd autofusion-mcp
npm install
npm run build
```

### 2. Configure Environment

Update `.env` with your system paths:

```env
# Path to Java and your shaded Autofusion Core CLI JAR
JAVA_BIN=java
AUTOFUSION_JAR=/absolute/path/to/autofusion-1.0.0-shaded.jar
AUTOFUSION_HEAP=-Xmx2g
```

**Important**: Use absolute paths for `AUTOFUSION_JAR` to avoid path resolution issues.

### 3. Test Local Server

```bash
npm run start
```

You should see: `[autofusion-mcp] unified router ready`

Stop with `Ctrl+C`.

## GitHub Copilot Integration

### Option 1: Repository-specific (Recommended)

Create `.vscode/mcp.json` in your project root:

```json
{
  "autofusion-mcp": {
    "command": "node",
    "args": ["./dist/server.js"],
    "env": {
      "JAVA_BIN": "java",
      "AUTOFUSION_JAR": "/absolute/path/to/autofusion-1.0.0-shaded.jar",
      "AUTOFUSION_HEAP": "-Xmx2g"
    }
  }
}
```

**Benefits**: Shared with team, travels with repository, relative paths supported.

### Option 2: Personal/Global Configuration

Add to your VS Code `settings.json`:

```json
{
  "github.copilot.mcp.servers": {
    "autofusion-mcp": {
      "command": "node",
      "args": ["/absolute/path/to/autofusion-mcp/dist/server.js"],
      "env": {
        "JAVA_BIN": "java",
        "AUTOFUSION_JAR": "/absolute/path/to/autofusion-1.0.0-shaded.jar",
        "AUTOFUSION_HEAP": "-Xmx2g"
      }
    }
  }
}
```

### Setup Steps

1. Choose configuration method above
2. Update `AUTOFUSION_JAR` path for your system
3. Restart VS Code
4. Click "Start" button in `.vscode/mcp.json` (if using Option 1)
5. Verify `autofusion_compare` tool appears in Copilot Chat tools list

## Usage Examples

### Excel Comparison with Natural Language

```
Compare C:\data\trades_old.xlsx vs C:\data\trades_new.xlsx on sheet "Portfolio".
Use keys TradeId,AsOfDate. Apply 2% tolerance on Amount.
Ignore sheets Archive,Temp. Output to C:\results.
```

### CSV Comparison

```
Compare sales_q1.csv and sales_q2.csv using account_id as primary key.
Ignore columns updated_at,ingest_ts. Use comma delimiter.
```

### Table Comparison (Inline JSON)

```
Compare these tables by UNL_KEY:
source=[{"UNL_KEY":"1","amt":"100"},{"UNL_KEY":"2","amt":"200"}]
target=[{"UNL_KEY":"1","amt":"100"},{"UNL_KEY":"2","amt":"205"}]
Apply 2% tolerance on amt.
```

## Tool Parameters

### Required for File Comparisons
- `file1`: Path to first file
- `file2`: Path to second file

### Excel-Specific Parameters
- `sheet`: Sheet name to compare
- `headerRow`: Header row number (default: 1)
- `dataRowStart`: First data row (default: 2)
- `ignoreSheets`: Array of sheet names to skip
- `mode`: Comparison mode (`uniqueKey` or `rowDiff`)

### CSV-Specific Parameters
- `delimiter`: Field separator (default: comma)
- `skipHeader`: Whether to skip header row
- `primaryKey`: Primary key column(s) for comparison

### Table-Specific Parameters
- `source`: Source data as JSON array
- `target`: Target data as JSON array
- `uniKey`: Unique key field name

### Common Parameters
- `keys`: Array of key columns for matching
- `ignoreColumns`: Columns to exclude from comparison
- `thresholds`: Tolerance percentages per column `{"Amount": 2.5}`
- `outDir`: Output directory path
- `dryRun`: Validation mode (default: true)

## Workflow Pattern

### 1. Dry-Run Validation
```
Call with dryRun=true (default)
→ Server validates inputs
→ Returns status and questions if needed
```

### 2. User Confirmation
```
If status=need_info → Answer questions
If status=ok → Proceed to execution
```

### 3. Actual Execution
```
Call with dryRun=false + normalizedArgs
→ Server executes comparison
→ Returns result file paths
```

## Routing Logic

The server automatically determines comparison type:

1. **Table Mode**: If both `source` and `target` JSON arrays provided
2. **CSV Mode**: If both file paths end with `.csv`
3. **Excel Mode**: Default for `.xlsx`, `.xlsm`, `.xls` files

## Natural Language Parsing

The server extracts parameters from prompts:

- **Sheet**: `sheet "Portfolio"` → `sheet: "Portfolio"`
- **Keys**: `keys TradeId,AsOfDate` → `keys: ["TradeId", "AsOfDate"]`
- **Thresholds**: `2% on Amount` → `thresholds: {"Amount": 2}`
- **Ignore**: `ignore sheets Archive,Temp` → `ignoreSheets: ["Archive", "Temp"]`

## Error Handling

### Common Issues

**"Java not found"**
- Ensure `JAVA_BIN` points to valid Java executable
- Try full path: `/usr/bin/java` or `C:\Program Files\Java\...\bin\java.exe`

**"JAR not found"**
- Verify `AUTOFUSION_JAR` uses absolute path
- Check file permissions and accessibility

**"Tools not visible in Copilot"**
- Verify MCP configuration paths are absolute
- Restart VS Code after configuration changes
- Check server starts successfully with `npm run start`

**Out of Memory**
- Increase `AUTOFUSION_HEAP` (e.g., `-Xmx4g`)
- Optimize for large files if needed

### Debugging

```bash
# Test server startup
npm run start

# Verify environment variables
echo $AUTOFUSION_JAR

# Test Java CLI directly
java -Xmx2g -jar /path/to/autofusion-1.0.0-shaded.jar --help
```

## Output Files

Successful comparisons generate:
- `summary.xlsx`: High-level comparison results
- `detail.xlsx`: Detailed differences
- `diffs.xlsx`: Raw difference data
- Execution metadata (elapsed time, status)

## Development

### Project Structure
```
autofusion-mcp/
├─ .env                 # Environment configuration
├─ .gitignore          # Git ignore patterns
├─ package.json        # Node.js dependencies
├─ tsconfig.json       # TypeScript configuration
├─ src/
│  └─ server.ts        # MCP server implementation
└─ dist/               # Compiled JavaScript (generated)
   └─ server.js
```

### Building from Source
```bash
npm run build    # Compile TypeScript
npm run dev      # Development mode with ts-node
npm run start    # Production mode
```

### Extending Functionality

To add new comparison modes or parameters:

1. Update `CompareArgs` schema in `src/server.ts`
2. Extend natural language parsing in `parseFromPrompt()`
3. Add flag generation logic for new modes
4. Rebuild with `npm run build`

## Troubleshooting

### Performance Optimization
- Tune `AUTOFUSION_HEAP` based on file sizes
- Use SSD storage for temporary files
- Consider timeout limits for very large comparisons

### Security Considerations
- Restrict file path access with allow-lists if needed
- Validate input file paths and extensions
- Run with minimal required permissions

### Monitoring
- Server outputs JSON-only to stdout
- Diagnostic messages go to stderr
- Log levels controlled by Java CLI configuration

## License

This MCP server is a wrapper around your Autofusion Core CLI. Refer to your Autofusion Core licensing terms for usage rights and restrictions.