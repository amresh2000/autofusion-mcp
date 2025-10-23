# Autofusion MCP Server

MCP (Model Context Protocol) Server for Autofusion Excel/CSV comparison tools, designed for seamless integration with VSCode and IntelliJ IDEA.

## 🆕 Latest Updates - Production Ready v2.0

**✅ ENTERPRISE-READY: Unified Comparison Architecture**
- **Single Excel Tool**: One `excel_compare` tool with multi-column key support
- **Single CSV Tool**: One `csv_compare` tool with composite key functionality
- **Mandatory 4-Sheet Reports**: All comparisons generate professional Excel reports with guaranteed output paths
- **Fixed Parameter Consistency**: Resolved `uniqueKeyColumn` vs `uniqueKey` inconsistency issues
- **Multi-Column Primary Keys**: Support for composite keys like `"CustomerID,Region,Date"`
- **Interactive Parameter Prompting**: Automatic validation and prompting via VSCode/IntelliJ MCP integration

**Key Production Features:**
- **Unified Architecture**: Excel uses `ExcelComparisonEngine` + `AfTableComparisonCTRLV2`, CSV uses `AfCsvReader` + `AfTableComparisonCTRLV2`
- **Mandatory Excel Reports**: Both CSV and Excel comparisons always generate comprehensive 4-sheet Excel reports
- **Required Parameters**: All critical parameters are mandatory with automatic IDE prompting
- **Reliable MCP Integration**: Production-tested wrapper classes with autofusion core library integration
- **VSCode/IntelliJ Ready**: Optimized for professional IDE integration, not GitHub Copilot

## Overview

This project provides a production-ready MCP server that exposes file comparison and API testing capabilities through standardized MCP tools. It integrates seamlessly with VSCode and IntelliJ IDEA to provide AI-powered file comparison and API testing capabilities directly in your development workflow.

## Architecture

- **Language**: Java 17
- **Dependencies**: autofusion (Java 11), MCP SDK, Jackson, RestAssured
- **Protocol**: Model Context Protocol via stdio transport
- **Integration**: Optimized for VSCode and IntelliJ IDEA MCP support

## Available Tools

This MCP server provides 4 specialized tools for file comparison and API testing:

### 1. `api_request`
Send HTTP API requests with comprehensive configuration options.

**Parameters:**
- `method` (required): HTTP method (GET, POST, PUT, DELETE, PATCH)
- `url` (required): Target URL
- `headers` (optional): HTTP headers as key-value pairs
- `queryParams` (optional): Query parameters as key-value pairs
- `body` (optional): Request body (JSON)
- `authType` (optional): Authentication type (BEARER, BASIC, API_KEY)
- `authValue` (optional): Authentication value/credentials

### 2. `api_schema_validator`
Validate API responses against JSON schemas.

**Parameters:**
- `method` (required): HTTP method
- `url` (required): Target URL
- `headers`, `queryParams`, `body`, `authType`, `authValue` (optional): Same as api_request
- `jsonSchemaRaw` (optional): Raw JSON schema string
- `jsonSchemaFilePath` (optional): Path to JSON schema file

### 3. `excel_compare`
Compare two Excel files using unified ExcelComparisonEngine + AfTableComparisonCTRLV2 architecture. Always generates comprehensive 4-sheet Excel reports.

**Required Parameters:**
- `file1Path` (required): Path to first Excel file
- `file2Path` (required): Path to second Excel file
- `uniqueKey` (required): Column name(s) for row matching - supports multi-column keys
- `outputPath` (required): Directory path for generated Excel report (mandatory for guaranteed reports)

**Multi-Column Key Examples:**
- Single column: `"ID"` or `"EmployeeID"`
- Multiple columns: `"CustomerID,Region,Date"` or `"OrderID,LineItem"`

**Optional Parameters:**
- `thresholds` (optional): Map of column names to percentage thresholds for numeric comparisons
- `ignoreColumns` (optional): Comma-separated column names to ignore during comparison
- `sourceSheetName` (optional): Name of sheet in source file (default: "Sheet1")
- `targetSheetName` (optional): Name of sheet in target file (default: "Sheet1")

**Automatic 4-Sheet Report Contents:**
- **Sheet 1 (Summary)**: Overall comparison statistics and metadata
- **Sheet 2 (Mismatches)**: Detailed mismatch information with source/target values
- **Sheet 3 (Source Extra)**: Records only present in source file
- **Sheet 4 (Target Extra)**: Records only present in target file

### 4. `csv_compare`
Compare two CSV files using unified AfCsvReader + AfTableComparisonCTRLV2 architecture. Always generates comprehensive 4-sheet Excel reports.

**Required Parameters:**
- `file1Path` (required): Path to first CSV file
- `file2Path` (required): Path to second CSV file
- `uniqueKey` (required): Column name(s) for row matching - supports multi-column keys
- `outputPath` (required): Directory path for generated Excel report (mandatory for guaranteed reports)

**Multi-Column Key Examples:**
- Single column: `"id"` or `"ProductCode"`
- Multiple columns: `"CustomerID,Region"` or `"OrderDate,CustomerID,ProductID"`

**Optional Parameters:**
- `delimiter1` (optional): Delimiter for first CSV (auto-detected if not specified)
- `delimiter2` (optional): Delimiter for second CSV (auto-detected if not specified)
- `skipHeader1` (optional): Skip header in first file (default: false)
- `skipHeader2` (optional): Skip header in second file (default: false)
- `thresholds` (optional): Map of column names to percentage thresholds for numeric comparisons
- `ignoreColumns` (optional): Comma-separated column names to ignore during comparison

**🆕 Automatic Delimiter Detection:**
- **Smart Detection**: Automatically detects comma, semicolon, tab, pipe, and other common delimiters
- **Per-File Detection**: Each file can have different delimiters (e.g., file1 = comma, file2 = semicolon)
- **User Override**: Manual delimiter specification bypasses auto-detection
- **Powered by Univocity**: Enterprise-grade parsing with robust edge case handling

**Automatic 4-Sheet Excel Report Contents:**
- **Sheet 1 (Summary)**: Overall comparison statistics and metadata
- **Sheet 2 (Mismatches)**: Detailed mismatch information with source/target values
- **Sheet 3 (Source Extra)**: Records only present in source file
- **Sheet 4 (Target Extra)**: Records only present in target file

## Prerequisites

1. **Java 17** - Required for autofusion-mcp
2. **Java 11** - Required for autofusion core library
3. **Maven 3.6+** - For building the project

## Building

1. First, build and install the autofusion core library:
```bash
cd /path/to/autofusion
mvn clean install
```

2. Then build the MCP server:
```bash
cd autofusion-mcp
mvn clean compile
```

## Running the MCP Server

### Via Maven
```bash
mvn exec:java
```

### Via JAR
```bash
mvn package
java -jar target/autofusion-mcp-1.0.0-shaded.jar
```

## VSCode and IntelliJ IDEA Integration

The server communicates via stdio transport and integrates with VSCode and IntelliJ IDEA's MCP support for professional development workflows.

### VSCode Configuration

1. Install the MCP extension for VSCode
2. Add to your VSCode MCP configuration file:
```json
{
  "mcpServers": {
    "autofusion": {
      "command": "java",
      "args": ["-jar", "/path/to/autofusion-mcp-1.0.0-shaded.jar"]
    }
  }
}
```

3. Restart VSCode to load the MCP server

### IntelliJ IDEA Configuration

1. Install the MCP plugin for IntelliJ IDEA
2. Configure the MCP server in IntelliJ settings:
   - **Server Name**: autofusion
   - **Command**: java
   - **Arguments**: -jar /path/to/autofusion-mcp-1.0.0-shaded.jar

3. Restart IntelliJ IDEA to activate the integration

### Interactive Parameter Prompting

Both VSCode and IntelliJ IDEA will automatically prompt for required parameters when you use the comparison tools:

**Example User Experience:**
```
User: "Compare these Excel files"
IDE: Shows interactive form with fields:
├─ file1Path: [Browse for Excel file...]
├─ file2Path: [Browse for Excel file...]
├─ uniqueKey: [text input with validation]
└─ outputPath: [Browse for directory...]
```

The MCP client validates all required parameters before sending requests and provides helpful error messages for missing or invalid inputs.

## Sample Prompts for Professional Usage

### 📊 Excel Comparison (`excel_compare`)

**Basic Excel Comparison with Single Key:**
```
Compare quarterly reports /reports/Q1_2024.xlsx and /reports/Q2_2024.xlsx using "TransactionID" as unique key
```

**Multi-Column Key Comparison:**
```
Compare customer data files using composite key "CustomerID,Region,Date" to match records across multiple dimensions
```

**Financial Data with Thresholds:**
```
Compare financial statements allowing 5% tolerance for "Amount" column and 2% for "TaxRate" column
```

**Audit Report with Ignored Columns:**
```
Compare audit files ignoring timestamp columns: "LastModified,CreatedDate,ProcessedBy"
```

### 📈 CSV Comparison (`csv_compare`)

**Basic CSV Comparison with Auto-Detection:**
```
Compare sales data files /data/sales_2023.csv and /data/sales_2024.csv using "OrderID" as unique key
```
*Note: Delimiters are automatically detected - no need to specify comma, semicolon, tab, etc.*

**Multi-Column Key for Complex Matching:**
```
Compare inventory files using "ProductCode,Location,Date" as composite key for precise matching
```

**🆕 Automatic Mixed Delimiter Detection:**
```
Compare files with different formats - automatically detects file1.csv (semicolon-separated) vs file2.csv (comma-separated)
```

**Manual Delimiter Override:**
```
Compare pipe-separated file1.csv with comma-separated file2.csv using "ID" key, save report to /reports/
```
*Note: Manual specification bypasses auto-detection when needed*

**ETL Pipeline Validation:**
```
Compare ETL outputs with 1% numeric tolerance, skip headers, and ignore audit columns: "created_by,modified_date"
```

### 🔧 API Testing (`api_request` & `api_schema_validator`)

**REST API Testing:**
```
Test the user API endpoint GET /api/v1/users/123 with Bearer token authentication
```

**JSON Schema Validation:**
```
Validate API response structure against OpenAPI schema for data quality assurance
```

## Advanced Features

### 🆕 Automatic CSV Delimiter Detection

**Enterprise-Grade Detection:**
- **Powered by Univocity Parsers**: Production-tested library with robust detection algorithms
- **Supports Common Delimiters**: Comma (`,`), semicolon (`;`), tab (`\t`), pipe (`|`), colon (`:`)
- **Quote-Aware Parsing**: Handles quoted fields containing delimiters (e.g., `"Smith, John", 25, "Engineer"`)
- **Performance Optimized**: Sample-based detection (5-10ms per file, independent of file size)

**Detection Process:**
1. **Sample Analysis**: Reads first ~50 rows to analyze delimiter patterns
2. **Statistical Scoring**: Evaluates consistency of field counts across rows
3. **Format Validation**: Ensures detected delimiter produces valid CSV structure
4. **Fallback Safety**: Defaults to comma if detection fails or file is malformed

**User Experience:**
```
// User doesn't specify delimiters
Compare /data/report1.csv with /data/report2.csv using "ID" as key

// System automatically detects and logs:
[INFO] Auto-detected delimiter 'SEMICOLON' for file1: /data/report1.csv
[INFO] Auto-detected delimiter 'COMMA' for file2: /data/report2.csv
```

**Manual Override:**
```json
{
  "file1Path": "/data/file1.csv",
  "file2Path": "/data/file2.csv",
  "delimiter1": "|",     // Manual override for file1
  "delimiter2": null,    // Auto-detect for file2
  "uniqueKey": "ID"
}
```

### Multi-Column Primary Key Support

**CSV Implementation:**
- Input: `"CustomerID,Region,Date"`
- Creates composite key: `"CUST001|NA|2024-01-01"`
- Uses pipe separator to avoid conflicts with data values

**Excel Implementation:**
- Input: `"OrderID,LineItem"`
- Autofusion engine handles multi-column keys natively
- Full support for complex primary key scenarios

### Automatic Report Generation

**File Naming Convention:**
```
Format: {file1}_vs_{file2}_{type}_comparison_{timestamp}.xlsx
Example: sales_Q1_vs_sales_Q2_csv_comparison_2024-01-15_14-30-22.xlsx
```

**Report Location:**
- User specifies output directory via `outputPath` parameter
- Filename automatically generated with timestamp
- Guaranteed unique filenames prevent overwrites

### Production Error Handling

**Comprehensive Validation:**
- File existence and readability checks
- Parameter validation with clear error messages
- Multi-column key validation against CSV headers
- Autofusion core library integration error handling

**Error Response Examples:**
```
"Missing required parameter: uniqueKey"
"Key column 'CustomerID' not found in CSV headers: [id, name, amount]"
"Cannot read Excel file: /path/to/missing_file.xlsx"
```

## Development

### Project Structure
```
autofusion-mcp/
├── pom.xml
├── README.md
└── src/main/java/com/cacib/auf/mcp/
    ├── MCPApplication.java          # Main server application
    ├── MCPApiTools.java             # HTTP API utilities
    ├── ExcelComparisonMCPWrapper.java # Excel comparison wrapper
    ├── CsvComparisonMCPWrapper.java   # CSV comparison wrapper
    ├── CsvDelimiterDetector.java     # Automatic delimiter detection
    └── CsvToTableConverter.java      # CSV to table converter with multi-key support
```

### Dependencies
- **autofusion** (1.0.0): Core comparison engines and models
- **MCP SDK** (0.9.0): Model Context Protocol framework
- **Univocity Parsers** (2.9.1): Enterprise-grade CSV delimiter detection
- **Jackson** (2.17.2): JSON processing
- **RestAssured** (5.3.2): HTTP client for API testing
- **SLF4J** (2.0.9): Logging framework

## Production Benefits

This MCP server transforms VSCode and IntelliJ IDEA into powerful data analysis and API testing environments:

- **🤖 Natural Language Queries**: Ask your IDE to compare files using plain English
- **🔄 Automated Workflows**: Chain together API testing and file comparison tasks
- **📊 Data Validation**: Validate data transformations and ETL pipeline outputs
- **🧪 API Testing**: Test and validate API responses without leaving your IDE
- **📈 Professional Reports**: Generate comparison reports for business analysis
- **⚡ Developer Productivity**: Eliminate manual file comparison and API testing
- **🔒 Enterprise Security**: Local processing, no data sent to external services

## Comparison Features

### Excel Comparison
- **🆕 Unified Architecture**: Uses ExcelComparisonEngine + AfTableComparisonCTRLV2 for comprehensive analysis
- **🆕 Multi-Column Keys**: Support for composite primary keys like "OrderID,LineItem"
- **🆕 Mandatory 4-Sheet Reports**: Professional reports with Summary, Mismatches, Source Extra, and Target Extra sheets
- **🆕 Required Output Paths**: Guaranteed report generation with user-specified directories
- **🆕 Numeric Thresholds**: Percentage-based tolerance for numeric column comparisons
- **🆕 Column Exclusion**: Ignore specific columns during comparison (timestamps, audit fields, etc.)
- **🆕 Multi-Sheet Support**: Compare different sheet names between source and target files
- **Professional Reporting**: Generates comprehensive Excel reports with detailed statistics
- **Smart Detection**: Automatically identifies structural and data differences

### CSV Comparison
- **🆕 Multi-Column Keys**: Composite key support for complex matching scenarios
- **🆕 Unified Architecture**: Same comparison engine foundation as Excel comparisons
- **🆕 Automatic Excel Reports**: All CSV comparisons generate 4-sheet Excel reports
- **🚀 Automatic Delimiter Detection**: Enterprise-grade detection using Univocity parsers
- **🔧 Mixed Format Support**: Handles files with different delimiters automatically
- **⚡ Performance Optimized**: Sample-based detection independent of file size
- **🛡️ Robust Parsing**: Quote-aware detection handles complex CSV edge cases
- **🎛️ User Override**: Manual delimiter specification bypasses auto-detection
- **Header Handling**: Configurable header row processing for each file independently
- **Numeric Thresholds**: Percentage-based tolerance for numeric comparisons per column
- **Detailed Reporting**: Comprehensive mismatch analysis with row-level and column-level details

### API Testing
- **Multiple HTTP Methods**: GET, POST, PUT, DELETE, PATCH support
- **Authentication**: Bearer token, Basic auth, API key support
- **Schema Validation**: JSON schema validation of API responses
- **Custom Headers**: Full control over request headers and query parameters
- **Error Handling**: Comprehensive error reporting for debugging

## Error Handling

All tools provide comprehensive error handling with detailed error messages:
- File validation (existence, readability)
- Parameter validation with clear messaging
- Multi-column key validation against file headers
- Comparison engine errors with context
- MCP protocol errors with troubleshooting guidance

## Logging

Uses SLF4J with simple console logging. Adjust logging level via system properties:
```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG -jar autofusion-mcp.jar
```

## Professional Integration Tips

### Best Practices

1. **Use Absolute Paths**: Provide full file paths for better reliability
   ```
   Compare /Users/analyst/data/file1.csv with /Users/analyst/data/file2.csv
   ```

2. **Specify Multi-Column Keys**: Use composite keys for complex data relationships
   ```
   Compare files using composite key "CustomerID,Region,Date" for precise matching
   ```

3. **Directory-Based Output**: Specify output directories for organized report storage
   ```
   Save comparison reports to /reports/monthly/ directory
   ```

4. **Chain Operations**: Combine multiple tools in enterprise workflows
   ```
   Test the data API, then compare the response with our expected CSV baseline
   ```

### Troubleshooting

- **Parameter Validation**: Missing required parameters are automatically prompted by the IDE
- **File Access**: Ensure Java process has read access to comparison files and write access to output directories
- **Multi-Column Keys**: Verify column names exist in both files when using composite keys
- **Large Files**: For files >100MB, consider splitting into smaller chunks for optimal performance

### Performance Optimization

- **Memory Management**: Excel comparisons are optimized with table-based analysis
- **Concurrent Operations**: Multiple comparisons can run simultaneously
- **Report Storage**: Use dedicated directories for organized report management
- **Network APIs**: Configure appropriate timeouts for API testing in enterprise environments

## License

This project follows the same licensing as the autofusion core library.

---

## Production Readiness Checklist

✅ **Core Functionality**: Unified comparison architecture implemented
✅ **Parameter Consistency**: All parameter naming conflicts resolved
✅ **Multi-Column Keys**: Composite key support for complex data relationships
✅ **Mandatory Reports**: 4-sheet Excel reports generated for all comparisons
✅ **Error Handling**: Comprehensive validation and error messaging
✅ **IDE Integration**: Optimized for VSCode and IntelliJ IDEA MCP support
✅ **Documentation**: Complete usage and integration documentation
✅ **Testing**: Validated with real CSV and Excel files

**Ready for enterprise deployment and professional development workflows.**