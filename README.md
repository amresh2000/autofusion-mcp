# Autofusion MCP Server

MCP (Model Context Protocol) Server for Autofusion Excel/CSV comparison tools, designed for GitHub Copilot integration.

## Overview

This project provides a MCP server that exposes file comparison and API testing capabilities through standardized MCP tools. It integrates seamlessly with GitHub Copilot to provide AI-powered file comparison and API testing capabilities directly in your development workflow.

## Architecture

- **Language**: Java 17
- **Dependencies**: autofusion (Java 11), MCP SDK, Jackson, RestAssured
- **Protocol**: Model Context Protocol via stdio transport
- **Integration**: Optimized for GitHub Copilot MCP support

## Available Tools

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
Compare two Excel files with advanced configuration options and multiple comparison modes.

**Parameters:**
- `file1Path` (required): Path to first Excel file
- `file2Path` (required): Path to second Excel file
- `outputPath` (optional): Path for comparison output file
- `sheetName` (optional): Specific sheet to compare
- `compareMode` (optional): "row_difference" (default) or "cell_by_cell"
- `headerRow` (optional): Row number containing headers (default: 1)
- `dataStartRow` (optional): First row containing data (default: 2)
- `keyColumns` (optional): Comma-separated key columns for matching
- `ignoredSheets` (optional): Comma-separated sheet names to ignore
- `outputDir` (optional): Custom output directory
- `outputFileName` (optional): Custom output file name

### 4. `csv_compare`
Compare two CSV files with flexible configuration and advanced V2 features.

**Parameters:**
- `file1Path` (required): Path to first CSV file
- `file2Path` (required): Path to second CSV file
- `delimiter1` (optional): Delimiter for first CSV (default: ",")
- `delimiter2` (optional): Delimiter for second CSV (default: ",")
- `skipHeader1` (optional): Skip header in first file (default: false)
- `skipHeader2` (optional): Skip header in second file (default: false)
- `uniqueKeyColumn` (optional): Name of unique key column (default: "UNL_KEY")
- `thresholds` (optional): Numeric comparison thresholds per column
- `customUniqueKey` (optional): Custom unique key column name (overrides uniqueKeyColumn)
- `ignoreColumns` (optional): CSV string of columns to ignore during comparison

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

## Usage with GitHub Copilot

The server communicates via stdio transport and integrates with GitHub Copilot's MCP support.

### Configuration

1. Add to your GitHub Copilot MCP configuration:
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

2. Restart GitHub Copilot to load the MCP server.

## Sample Prompts for Each Tool

### 🔧 API Testing (`api_request` & `api_schema_validator`)

**Basic API Request:**
```
Test the GET endpoint at https://api.example.com/users/123 with Bearer token authentication
```

**API with Custom Headers:**
```
Send a POST request to https://api.example.com/data with JSON body {"name": "test"} and Content-Type application/json header
```

**API Schema Validation:**
```
Call the API at https://jsonplaceholder.typicode.com/posts/1 and validate the response against this schema:
{
  "type": "object",
  "properties": {
    "id": {"type": "number"},
    "title": {"type": "string"},
    "body": {"type": "string"}
  },
  "required": ["id", "title", "body"]
}
```

**API with Query Parameters:**
```
Test the GitHub API: GET https://api.github.com/search/repositories with query parameters q=java and sort=stars
```

### 📊 Excel Comparison (`excel_compare`)

**Basic Excel Comparison:**
```
Compare two Excel files: /data/report_jan.xlsx and /data/report_feb.xlsx using row difference mode
```

**Cell-by-Cell Excel Comparison:**
```
Compare /data/financial_Q1.xlsx with /data/financial_Q2.xlsx using cell-by-cell comparison mode and save results to /output/diff.xlsx
```

**Specific Sheet Comparison:**
```
Compare only the "Summary" sheet between /data/budget_2023.xlsx and /data/budget_2024.xlsx
```

**🆕 Advanced Excel with Custom Header Rows:**
```
Compare Excel files /data/report1.xlsx and /data/report2.xlsx where headers are in row 3, data starts from row 5
```

**🆕 Multi-Key Excel Comparison:**
```
Compare financial reports /data/budget_2023.xlsx and /data/budget_2024.xlsx using Account_Number and Department as composite key columns
```

**🆕 Excel with Sheet Exclusion:**
```
Compare /data/workbook1.xlsx with /data/workbook2.xlsx using Cost_Center,GL_Account as key columns, ignore sheets: Notes,References,Archive
```

**🆕 Complex Excel Structure:**
```
Compare Excel files with non-standard format: headers in row 2, data from row 4, using Product_ID,Location as keys, ignore Audit and Summary sheets
```

**🆕 Excel with Custom Output Management:**
```
Compare /reports/monthly_data.xlsx files with custom output directory /results/ and filename monthly_comparison_2024.xlsx
```

### 📈 CSV Comparison (`csv_compare`)

**Different Delimiters:**
```
Compare these CSV files with different separators:
- File 1: /data/report1.csv (semicolon-separated)
- File 2: /data/report2.csv (comma-separated)
```

**With Header Handling:**
```
Compare /data/sales_data.csv and /data/sales_updated.csv, skipping the header row in both files
```

**Numeric Thresholds:**
```
Compare /data/financial_data.csv with /data/financial_revised.csv allowing 5% tolerance for the "Amount" column and 2% for "Tax" column
```

**Custom Unique Key:**
```
Compare CSV files /data/employees.csv and /data/employees_new.csv using "EmployeeID" as the unique key column
```

**Tab-Separated Files:**
```
Compare two TSV files: /data/data1.tsv and /data/data2.tsv (both tab-separated) with custom unique key "ID"
```

**🆕 Enhanced CSV with Custom Unique Key (V2):**
```
Compare /data/employees.csv and /data/employees_updated.csv using 'EmployeeID' as unique key instead of default UNL_KEY
```

**🆕 Privacy-Focused CSV Comparison (V2):**
```
Compare customer data files /data/customers_old.csv and /data/customers_new.csv, ignoring sensitive columns: password,credit_card,ssn
```

**🆕 Advanced CSV with Custom Key and Column Exclusion (V2):**
```
Compare /reports/Q1_sales.csv with /reports/Q2_sales.csv using ProductCode as unique key, skip headers, and ignore audit columns: created_date,modified_by,last_updated
```

**🆕 Secure Data Comparison (V2):**
```
Compare employee records with custom unique key 'EmpID' and ignore sensitive data: Salary,SSN,BankAccount,PersonalEmail
```

### 🔄 Complex Workflows

**Multi-Step Analysis:**
```
1. First compare the Excel files /data/baseline.xlsx and /data/current.xlsx
2. Then test the API endpoint https://api.company.com/upload to verify it accepts the data format
3. Finally compare the resulting CSV export with /data/expected_output.csv
```

**Data Validation Pipeline:**
```
Compare the CSV outputs from our ETL pipeline:
- Source: /etl/raw_data.csv (pipe-separated)
- Processed: /etl/processed_data.csv (comma-separated)
- Allow 1% threshold for numeric fields and skip headers in both
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
    └── CsvToTableConverter.java      # CSV to table converter
```

### Dependencies
- **autofusion** (1.0.0): Core comparison engines and models
- **MCP SDK** (0.9.0): Model Context Protocol framework
- **Jackson** (2.17.2): JSON processing
- **RestAssured** (5.3.2): HTTP client for API testing
- **SLF4J** (2.0.9): Logging framework

## Why Use with GitHub Copilot?

This MCP server transforms GitHub Copilot into a powerful data analysis and API testing assistant:

- **🤖 Natural Language**: Ask Copilot to compare files using plain English
- **🔄 Automated Workflows**: Chain together API testing and file comparison tasks
- **📊 Data Validation**: Validate data transformations and ETL pipeline outputs
- **🧪 API Testing**: Test and validate API responses without leaving your IDE
- **📈 Report Generation**: Generate comparison reports for data analysis
- **⚡ Developer Productivity**: Eliminate manual file comparison and API testing

## Comparison Features

### Excel Comparison
- **Row Difference Mode**: Compares Excel files row by row with detailed mismatch reporting
- **Cell-by-Cell Mode**: Compares individual cells for precise differences
- **Multi-sheet Support**: Can process multiple sheets in workbooks
- **Flexible Output**: Generates difference reports in Excel format
- **Smart Detection**: Automatically identifies structural and data differences

### CSV Comparison
- **Different Delimiters**: Support for different separators per file (comma, semicolon, tab, pipe, etc.)
- **Header Handling**: Configurable header row processing for each file independently
- **Numeric Thresholds**: Percentage-based tolerance for numeric comparisons per column
- **Key Column Management**: Automatic unique key assignment or custom key specification
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
- Parameter validation
- Comparison engine errors
- MCP protocol errors

## Logging

Uses SLF4J with simple console logging. Adjust logging level via system properties:
```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG -jar autofusion-mcp.jar
```

## GitHub Copilot Integration Tips

### Best Practices

1. **Be Specific with File Paths**: Use absolute paths for better reliability
   ```
   Compare /Users/john/data/file1.csv with /Users/john/data/file2.csv
   ```

2. **Specify Comparison Requirements**: Mention delimiters, headers, and thresholds upfront
   ```
   Compare pipe-separated CSV files, skipping headers, with 2% tolerance for numeric columns
   ```

3. **Chain Operations**: Combine multiple tools in a single workflow
   ```
   Test the API, then compare the response data with our expected CSV output
   ```

4. **Use Descriptive Names**: Help Copilot understand your intent
   ```
   Compare the baseline financial report with the updated quarterly report
   ```

### Troubleshooting

- **File Not Found**: Ensure file paths are correct and files exist
- **Permission Errors**: Check that the Java process has read access to your files
- **Delimiter Issues**: Specify custom delimiters when files use non-standard separators
- **Memory Issues**: For very large files, consider splitting them into smaller chunks

### Performance Tips

- **Large Files**: Use row_difference mode for Excel files rather than cell_by_cell for better performance
- **Network APIs**: Set reasonable timeouts for API testing
- **Batch Operations**: Group similar comparisons together for efficiency

## License

This project follows the same licensing as the autofusion core library.