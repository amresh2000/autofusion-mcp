# Autofusion-MCP Server v0.1.0

A Model Context Protocol (MCP) server that provides unified file and database comparison capabilities through your Autofusion Core CLI. This server exposes a single tool `autofusion_compare` that intelligently routes between Database, Excel, CSV, and Table comparison modes based on input patterns.

## Features

- **Single Unified Tool**: One `autofusion_compare` tool handles all comparison types
- **Intelligent Routing**: Automatically detects Database, Excel, CSV, or Table comparison based on inputs
- **Database Support**: Compare data across PostgreSQL, MySQL, Oracle, SQL Server, and H2 databases
- **Natural Language Processing**: Extracts parameters from human-readable prompts
- **Dry-Run Workflow**: Validates inputs before execution with user confirmation
- **Java CLI Integration**: Calls your existing Autofusion Core shaded JAR

## Prerequisites

- **Node.js**: Version 18+ (tested with Node.js 18.x, 20.x, 22.x)
- **Java**: Java 11+ runtime environment (tested with Java 11, 17, 21)
- **Autofusion Core**: Version 1.0.0+ with database comparison support (`autofusion-1.0.0-shaded.jar`)
- **Database Drivers**: JDBC drivers included in Autofusion JAR for:
  - PostgreSQL (org.postgresql.Driver)
  - MySQL (com.mysql.cj.jdbc.Driver)
  - Oracle (oracle.jdbc.driver.OracleDriver)
  - SQL Server (com.microsoft.sqlserver.jdbc.SQLServerDriver)
  - H2 (org.h2.Driver)
- **GitHub Copilot**: VS Code extension with MCP support

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

### Database Comparison

#### PostgreSQL Example
```
Compare customer data between production and staging databases.
Source: postgresql://user:pass@prod.db:5432/customers
SQL: "SELECT id, name, email, balance FROM customers WHERE created_date >= '2024-01-01'"
Target: postgresql://user:pass@staging.db:5432/customers
SQL: "SELECT id, name, email, balance FROM customers WHERE created_date >= '2024-01-01'"
Unique key: id. Apply 2% tolerance on balance. Ignore columns: updated_at,sync_status.
```

#### MySQL Example
```
Compare inventory data between warehouses.
Source: mysql://inventory:pass123@warehouse1.db:3306/inventory
Target: mysql://inventory:pass123@warehouse2.db:3306/inventory
Source SQL: "SELECT product_id, quantity, price FROM products WHERE category = 'electronics'"
Target SQL: "SELECT product_id, quantity, price FROM products WHERE category = 'electronics'"
Unique key: product_id. Apply 5% tolerance on price.
```

#### Oracle Example
```
Compare financial transactions using semicolon format.
Source: jdbc:oracle:thin:@finance1:1521:ORCL;finance_user;secure_pass;oracle.jdbc.driver.OracleDriver
Target: jdbc:oracle:thin:@finance2:1521:ORCL;finance_user;secure_pass;oracle.jdbc.driver.OracleDriver
SQL queries: "SELECT transaction_id, amount, currency FROM transactions WHERE date_created >= SYSDATE-30"
Unique key: transaction_id. Ignore columns: created_by,modified_date.
```

#### SQL Server Example
```
Test connection to SQL Server databases only.
Source: sqlserver://sa:password@server1:1433/SalesDB
Target: sqlserver://sa:password@server2:1433/SalesDB
Test connections without running queries.
```

**Database Connection Formats:**

**✅ Supported URL Formats:**
```bash
# PostgreSQL
postgresql://user:pass@host:5432/dbname

# MySQL
mysql://user:pass@host:3306/dbname

# SQL Server
sqlserver://user:pass@host:1433/dbname

# Oracle (using thin client)
oracle:thin://user:pass@host:1521/servicename
```

**✅ Supported Semicolon Formats:**
```bash
# PostgreSQL
jdbc:postgresql://host:5432/dbname;username;password;org.postgresql.Driver

# MySQL
jdbc:mysql://host:3306/dbname;username;password;com.mysql.cj.jdbc.Driver

# SQL Server
jdbc:sqlserver://host:1433;databaseName=dbname;username;password;com.microsoft.sqlserver.jdbc.SQLServerDriver

# Oracle
jdbc:oracle:thin:@host:1521:servicename;username;password;oracle.jdbc.driver.OracleDriver

# H2 (for testing)
jdbc:h2:mem:testdb;username;password;org.h2.Driver
```

**❌ Common Incorrect Formats:**
```bash
# DON'T MIX FORMATS - This will fail:
jdbc:postgresql://host:5432/dbname;user;pass;driver  # Mixed URL + semicolon

# DON'T USE JDBC PREFIX with URL format:
jdbc:postgresql://user:pass@host:5432/dbname  # Remove 'jdbc:' for URL format
```

**Auto-Detection**: Driver classes automatically detected from JDBC URLs

**Database Features:**
- **Long Query Support**: 10-minute default timeout for complex analytical queries
- **Connection Testing**: Validate database connectivity before comparison (`testConnection: true`)
- **Parallel Execution**: Source and target queries run concurrently for better performance
- **SQL Validation**: Pre-execution validation of SQL syntax and query structure
- **Transaction Safety**: Read-only operations with automatic connection cleanup
- **Error Recovery**: Comprehensive error handling with detailed diagnostic messages
- **Large Dataset Support**: Memory-efficient processing for queries returning millions of rows

### Excel Comparison with Enhanced Features

```
Compare C:\data\trades_old.xlsx vs C:\data\trades_new.xlsx on sheet "Portfolio".
Use uniqueKey mode with composite keys TradeId,AsOfDate.
Apply 2% tolerance on Amount, 5% tolerance on Price.
Ignore sheets Archive,Temp. Output to C:\results.
```

**Advanced Excel Features:**
- **Intelligent Key Detection**: Auto-suggests key columns based on naming patterns
- **Composite Key Support**: Use multiple columns as unique identifier
- **Rich Question Handling**: Interactive prompts for missing parameters
- **Three Comparison Modes**: Choose optimal method for your data structure

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

### Database-Specific Parameters
- `sourceDb`: Source database connection string
- `targetDb`: Target database connection string
- `sourceSql`: SQL query for source database
- `targetSql`: SQL query for target database
- `uniqueKey`: Primary key column for row matching
- `testConnection`: Test database connectivity (boolean)

### Excel-Specific Parameters
- `sheet`: Sheet name to compare (auto-detected if single sheet)
- `mode`: Comparison mode:
  - `uniqueKey` (recommended): Key-based comparison with intelligent matching
  - `rowDiff`: Row-by-row difference analysis
  - `cellByCell`: Cell-level granular comparison
- `keys`: Key column(s) for uniqueKey mode - supports composite keys like `["TradeId", "Date"]`
- `headerRow`: Header row number (default: 1)
- `dataRowStart`: First data row (default: 2)
- `ignoreSheets`: Array of sheet names to skip
- `thresholds`: Numeric comparison tolerances, e.g., `{"Amount": 2.0}` for 2% tolerance

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

### Complete Database Workflow Example

#### Step 1: Initial Request (Incomplete)
```
"Compare customer data between production and staging PostgreSQL databases"
```
**Response:** "I need more information to proceed:"
- Source database connection string required
- Target database connection string required
- Source SQL query required
- Target SQL query required
- Unique key column required

#### Step 2: Provide Connection Details
```
"Source: postgresql://readonly:pass@prod.db:5432/customers
Target: postgresql://readonly:pass@staging.db:5432/customers
Unique key: customer_id"
```
**Response:** "I need more information to proceed:"
- Source SQL query required
- Target SQL query required

#### Step 3: Complete Request
```
"Source SQL: SELECT customer_id, name, email, balance FROM customers WHERE status='active'
Target SQL: SELECT customer_id, name, email, balance FROM customers WHERE status='active'
Apply 2% tolerance on balance. Ignore updated_at column."
```
**Response:** "Ready to execute comparison. Configuration:
- Source: postgresql://readonly:***@prod.db:5432/customers
- Target: postgresql://readonly:***@staging.db:5432/customers
- Unique Key: customer_id
- Thresholds: {"balance": 2.0}
- Ignore Columns: ["updated_at"]

Call again with dryRun=false to execute."

#### Step 4: Execute Comparison
```
"Execute the comparison with dryRun=false"
```
**Response:** Comparison results with output files and statistics.

## Routing Logic

The server automatically determines comparison type:

1. **Database Mode**: If `sourceDb`, `targetDb`, `sourceSql`, `targetSql`, and `uniqueKey` provided
2. **Table Mode**: If both `source` and `target` JSON arrays provided
3. **CSV Mode**: If both file paths end with `.csv`
4. **Excel Mode**: Default for `.xlsx`, `.xlsm`, `.xls` files

## Natural Language Parsing

The server extracts parameters from prompts:

- **Sheet**: `sheet "Portfolio"` → `sheet: "Portfolio"`
- **Keys**: `keys TradeId,AsOfDate` → `keys: ["TradeId", "AsOfDate"]`
- **Thresholds**: `2% on Amount` → `thresholds: {"Amount": 2}`
- **Ignore**: `ignore sheets Archive,Temp` → `ignoreSheets: ["Archive", "Temp"]`
- **Database**: `source database: postgresql://user:pass@host:5432/db` → `sourceDb: "postgresql://..."`
- **SQL**: `source query: "SELECT * FROM customers"` → `sourceSql: "SELECT * FROM customers"`
- **Unique Key**: `unique key: customer_id` → `uniqueKey: "customer_id"`

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

### Database-Specific Issues

**"Invalid database configuration"**
- Verify connection string format matches supported patterns
- Check that all required parameters (host, port, database name) are provided
- Ensure no mixing of URL and semicolon formats

**"Connection failed"**
```bash
# Test connection first
"Test connection to source: postgresql://user:pass@host:5432/db"

# Common fixes:
- Verify host and port are accessible
- Check username/password credentials
- Ensure database name exists
- Confirm firewall rules allow connection
- Test with psql/mysql client first
```

**"Query timeout"**
- Queries timeout after 10 minutes by default
- Optimize query performance with indexes
- Consider limiting result set with WHERE clauses
- Use pagination for very large datasets

**"SQL validation failed"**
- Only SELECT statements are allowed
- Avoid DDL (CREATE, DROP, ALTER) and DML (INSERT, UPDATE, DELETE)
- Ensure proper SQL syntax for target database type
- Test queries in database client before using in comparison

**"Driver not found"**
- Driver auto-detection failed
- Use semicolon format with explicit driver class:
```bash
jdbc:postgresql://host:5432/db;user;pass;org.postgresql.Driver
```

**"Unique key column missing"**
- Ensure `uniqueKey` column exists in both query results
- Check column name spelling and case sensitivity
- Verify column appears in SELECT clause of both queries

### Debugging

```bash
# Test server startup
npm run start

# Verify environment variables
echo $AUTOFUSION_JAR
echo $JAVA_BIN

# Test Java CLI directly
java -Xmx2g -jar /path/to/autofusion-1.0.0-shaded.jar --help

# Test database connection directly
java -Xmx2g -jar /path/to/autofusion-1.0.0-shaded.jar db \
  --source "postgresql://user:pass@host:5432/db" \
  --target "postgresql://user:pass@host:5432/db" \
  --sourceSql "SELECT 1 as test" \
  --targetSql "SELECT 1 as test" \
  --uniqueKey "test" \
  --testConnection=true \
  --dryRun=true

# Check MCP server JSON-RPC communication
echo '{"jsonrpc":"2.0","method":"tools/list","id":1}' | node dist/server.js
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