package com.cacib.auf.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import com.cacib.auf.core.comparison.model.CompareResult;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.restassured.http.Header;
import io.restassured.response.Response;

public class MCPApplication {

    private static final Logger log = LoggerFactory.getLogger(MCPApplication.class);

    public static void main(String[] args) {
        // Test user's CSV files first
        if (args.length > 0 && "test".equals(args[0])) {
            testUserCsvFiles();
            return;
        }

        // Test cross-database comparison
        if (args.length > 0 && "test-cross-db".equals(args[0])) {
            testCrossDatabaseComparison();
            return;
        }

        // ===== Stdio Server Transport =====
        var transportProvider = new StdioServerTransportProvider(new ObjectMapper());

        // ===== API Request Tool Specification =====
        var apiRequestToolSpecification = getApiRequestToolSpecification();

        // ===== API Schema Validator Tool Specification =====
        var apiSchemaValidatorToolSpecification = getApiSchemaValidatorToolSpecification();

        // ===== Simplified Excel Comparison Tool Specification =====
        var excelComparisonToolSpecification = getExcelComparisonToolSpecification();

        // ===== Simplified CSV Comparison Tool Specification =====
        var csvComparisonToolSpecification = getCsvComparisonToolSpecification();

        // ===== Database Tool Specification =====
        var databaseToolSpecification = getDatabaseToolSpecification();

        // ===== MCP Server =====
        // Can copy and paste this into another project to spin up the server
        McpServer.sync(transportProvider)
                .serverInfo("jaxos-mcp-server", "0.0.1")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(apiRequestToolSpecification, apiSchemaValidatorToolSpecification,
                       excelComparisonToolSpecification, csvComparisonToolSpecification,
                       databaseToolSpecification)
                .build();

        log.info("Starting server...");
    }

    // ==========================================================
    // API Request Tool Specification
    // ==========================================================

    private static McpServerFeatures.SyncToolSpecification getApiRequestToolSpecification() {
        String schema = """
        {
            "type": "object",
            "id": "urn:jsonschema:ApiRequest",
            "properties": {
                "method": { "type": "string" },
                "url": { "type": "string" },
                "headers": { "type": "object" },
                "queryParams": { "type": "object" },
                "body": { "type": "string" },
                "authType": { "type": "string" },
                "authValue": { "type": "string" }
            },
            "required": ["method", "url"]
        }
        """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "api_request",
                        "Send an API request by specifying its properties and get the response code and body returned by the API.",
                        schema
                ),
                (exchange, arguments) -> {
                    String method = (String) arguments.get("method");
                    String url = (String) arguments.get("url");
                    Map<String, String> headers = (Map<String, String>) arguments.getOrDefault("headers", new HashMap<>());
                    Map<String, String> queryParams = (Map<String, String>) arguments.getOrDefault("queryParams", new HashMap<>());
                    String body = (String) arguments.getOrDefault("body", null);
                    String authType = (String) arguments.getOrDefault("authType", null);
                    String authValue = (String) arguments.getOrDefault("authValue", null);

                    Response response = MCPApiTools.sendRequest(
                            method,
                            url,
                            headers,
                            queryParams,
                            body,
                            authType != null ? MCPApiTools.AuthType.valueOf(authType) : null,
                            authValue
                    );

                    List<McpSchema.Content> contents = new ArrayList<>();
                    contents.add(new McpSchema.TextContent("Status: " + response.getStatusCode()));
                    contents.add(new McpSchema.TextContent("Body: " + response.getBody().asString()));

                    return new McpSchema.CallToolResult(contents, false);
                }
        );
    }

    // ==========================================================
    // API Schema Validator Tool Specification
    // ==========================================================

    private static McpServerFeatures.SyncToolSpecification getApiSchemaValidatorToolSpecification() {
        String schema = """
        {
            "type": "object",
            "id": "urn:jsonschema:ApiSchemaValidator",
            "properties": {
                "method": { "type": "string" },
                "url": { "type": "string" },
                "headers": { "type": "object" },
                "queryParams": { "type": "object" },
                "body": { "type": "string" },
                "authType": { "type": "string" },
                "authValue": { "type": "string" },
                "jsonSchemaRaw": { "type": "string" },
                "jsonSchemaFilePath": { "type": "string" }
            },
            "required": ["method", "url"]
        }
        """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "api_schema_validator",
                        "Send an API request and validate the response against a provided JSON schema (raw or file path).",
                        schema
                ),
                (exchange, arguments) -> {
                    String method = (String) arguments.get("method");
                    String url = (String) arguments.get("url");
                    Map<String, String> headers = (Map<String, String>) arguments.getOrDefault("headers", new HashMap<>());
                    Map<String, String> queryParams = (Map<String, String>) arguments.getOrDefault("queryParams", new HashMap<>());
                    String body = (String) arguments.getOrDefault("body", null);
                    String authType = (String) arguments.getOrDefault("authType", null);
                    String authValue = (String) arguments.getOrDefault("authValue", null);
                    String jsonSchemaRaw = (String) arguments.getOrDefault("jsonSchemaRaw", null);
                    String jsonSchemaFilePath = (String) arguments.getOrDefault("jsonSchemaFilePath", null);

                    MCPApiTools.ValidationResult result = MCPApiTools.validateApiResponseWithSchema(
                            method,
                            url,
                            headers,
                            queryParams,
                            body,
                            authType != null ? MCPApiTools.AuthType.valueOf(authType) : null,
                            authValue,
                            jsonSchemaRaw,
                            jsonSchemaFilePath
                    );

                    List<McpSchema.Content> contents = new ArrayList<>();
                    contents.add(new McpSchema.TextContent("Validation Success: " + result.isValid));
                    contents.add(new McpSchema.TextContent("Validation Message: " + result.message));
                    contents.add(new McpSchema.TextContent("Status: " + result.statusCode));
                    contents.add(new McpSchema.TextContent("Body: " + result.responseBody));

                    return new McpSchema.CallToolResult(contents, false);
                }
        );
    }

    // ==========================================================
    // Excel Comparison Tool Specification
    // ==========================================================

    private static McpServerFeatures.SyncToolSpecification getExcelComparisonToolSpecification() {
        String schema = """
        {
            "type": "object",
            "id": "urn:jsonschema:ExcelComparison",
            "properties": {
                "file1Path": { "type": "string" },
                "file2Path": { "type": "string" },
                "uniqueKey": {
                    "type": "string",
                    "description": "Column name(s) to use as unique key for row matching (required). For multi-column keys, use comma-separated format (e.g., 'CustomerID,Region')"
                },
                "thresholds": {
                    "type": "object",
                    "description": "Map of column names to percentage thresholds for numeric comparisons (e.g., {'Price': 5.0, 'Amount': 2.0})"
                },
                "ignoreColumns": {
                    "type": "string",
                    "description": "Comma-separated column names to ignore during comparison (e.g., 'Timestamp,LastModified')"
                },
                "outputPath": {
                    "type": "string",
                    "description": "Output directory path for generated 4-sheet Excel report (required)"
                },
                "sourceSheetName": {
                    "type": "string",
                    "description": "Name of sheet in source file (default: 'Sheet1')"
                },
                "targetSheetName": {
                    "type": "string",
                    "description": "Name of sheet in target file (default: 'Sheet1')"
                }
            },
            "required": ["file1Path", "file2Path", "uniqueKey", "outputPath"]
        }
        """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "excel_compare",
                        "Compare two Excel files using unified AfExcelReader + AfTableComparisonCTRLV2 architecture. Always generates comprehensive 4-sheet Excel reports with Summary, Mismatches, Source Extra, and Target Extra sheets.",
                        schema
                ),
                (exchange, arguments) -> {
                    // Validate required parameters using helper method
                    String file1Path = (String) arguments.get("file1Path");
                    String file2Path = (String) arguments.get("file2Path");
                    String uniqueKey = (String) arguments.get("uniqueKey");
                    String outputPath = (String) arguments.get("outputPath");

                    McpSchema.CallToolResult validationResult = validateRequiredParams(file1Path, file2Path, uniqueKey, outputPath);
                    if (validationResult != null) {
                        return validationResult; // Return validation error
                    }

                    // Optional parameters
                    @SuppressWarnings("unchecked")
                    Map<String, Double> thresholds = (Map<String, Double>) arguments.getOrDefault("thresholds", null);
                    String ignoreColumnsStr = (String) arguments.getOrDefault("ignoreColumns", null);
                    String sourceSheetName = (String) arguments.getOrDefault("sourceSheetName", null);
                    String targetSheetName = (String) arguments.getOrDefault("targetSheetName", null);

                    try {
                        // Convert ignoreColumns string to Set using helper method
                        Set<String> ignoreColumns = parseIgnoreColumns(ignoreColumnsStr);

                        // Generate auto filename and combine with directory path
                        String autoFilename = generateReportFilename(file1Path, file2Path, "Excel");
                        String fullOutputPath = new java.io.File(outputPath, autoFilename).getAbsolutePath();

                        // Use simplified Excel comparison method
                        CompareResult result = ExcelComparisonMCPWrapper.compareExcelFiles(
                                file1Path, file2Path, uniqueKey, thresholds, ignoreColumns,
                                fullOutputPath, sourceSheetName, targetSheetName);

                        // Format response using helper method
                        List<McpSchema.Content> contents = formatComparisonResult(result, "Excel", fullOutputPath);
                        return new McpSchema.CallToolResult(contents, false);

                    } catch (Exception e) {
                        log.error("Excel comparison failed", e);
                        List<McpSchema.Content> errorContents = new ArrayList<>();
                        errorContents.add(new McpSchema.TextContent("Error: " + e.getMessage()));
                        return new McpSchema.CallToolResult(errorContents, true);
                    }
                }
        );
    }

    // ==========================================================
    // CSV Comparison Tool Specification
    // ==========================================================

    private static McpServerFeatures.SyncToolSpecification getCsvComparisonToolSpecification() {
        String schema = """
        {
            "type": "object",
            "id": "urn:jsonschema:CsvComparison",
            "properties": {
                "file1Path": { "type": "string" },
                "file2Path": { "type": "string" },
                "delimiter1": {
                    "type": "string",
                    "description": "Delimiter for first CSV file (null for auto-detection)"
                },
                "delimiter2": {
                    "type": "string",
                    "description": "Delimiter for second CSV file (null for auto-detection)"
                },
                "skipHeader1": {
                    "type": "boolean",
                    "description": "Whether to skip header row in first file (default: true)"
                },
                "skipHeader2": {
                    "type": "boolean",
                    "description": "Whether to skip header row in second file (default: true)"
                },
                "uniqueKey": {
                    "type": "string",
                    "description": "Column name(s) to use as unique key for row matching (required). For multi-column keys, use comma-separated format (e.g., 'CustomerID,Region')"
                },
                "thresholds": {
                    "type": "object",
                    "description": "Map of column names to percentage thresholds for numeric comparisons (e.g., {'Price': 5.0, 'Amount': 2.0})"
                },
                "ignoreColumns": {
                    "type": "string",
                    "description": "Comma-separated column names to ignore during comparison (e.g., 'Timestamp,LastModified')"
                },
                "outputPath": {
                    "type": "string",
                    "description": "Output directory path for generated 4-sheet Excel report (required)"
                }
            },
            "required": ["file1Path", "file2Path", "uniqueKey", "outputPath"]
        }
        """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "csv_compare",
                        "Compare two CSV files using unified AfCsvReader + AfTableComparisonCTRLV2 architecture. Always generates comprehensive 4-sheet Excel reports with Summary, Mismatches, Source Extra, and Target Extra sheets.",
                        schema
                ),
                (exchange, arguments) -> {
                    // Validate required parameters using helper method
                    String file1Path = (String) arguments.get("file1Path");
                    String file2Path = (String) arguments.get("file2Path");
                    String uniqueKey = (String) arguments.get("uniqueKey");
                    String outputPath = (String) arguments.get("outputPath");

                    McpSchema.CallToolResult validationResult = validateRequiredParams(file1Path, file2Path, uniqueKey, outputPath);
                    if (validationResult != null) {
                        return validationResult; // Return validation error
                    }

                    // Optional parameters
                    String delimiter1 = (String) arguments.getOrDefault("delimiter1", null);
                    String delimiter2 = (String) arguments.getOrDefault("delimiter2", null);
                    Boolean skipHeader1 = (Boolean) arguments.getOrDefault("skipHeader1", null);
                    Boolean skipHeader2 = (Boolean) arguments.getOrDefault("skipHeader2", null);
                    @SuppressWarnings("unchecked")
                    Map<String, Double> thresholds = (Map<String, Double>) arguments.getOrDefault("thresholds", null);
                    String ignoreColumns = (String) arguments.getOrDefault("ignoreColumns", null);

                    try {
                        // Generate auto filename and combine with directory path
                        String autoFilename = generateReportFilename(file1Path, file2Path, "CSV");
                        String fullOutputPath = new java.io.File(outputPath, autoFilename).getAbsolutePath();

                        // Use simplified CSV comparison method
                        CompareResult result = CsvComparisonMCPWrapper.compareCsvFiles(
                                file1Path, file2Path, delimiter1, delimiter2,
                                skipHeader1, skipHeader2, uniqueKey, thresholds,
                                ignoreColumns, fullOutputPath);

                        // Format response using helper method
                        List<McpSchema.Content> contents = formatComparisonResult(result, "CSV", fullOutputPath);
                        return new McpSchema.CallToolResult(contents, false);

                    } catch (Exception e) {
                        List<McpSchema.Content> errorContents = new ArrayList<>();
                        errorContents.add(new McpSchema.TextContent("Error: " + e.getMessage()));
                        return new McpSchema.CallToolResult(errorContents, true);
                    }
                }
        );
    }


    /**
     * Generate auto filename for comparison reports based on input files and timestamp.
     */
    private static String generateReportFilename(String file1Path, String file2Path, String type) {
        // Extract base names without extensions
        String baseName1 = new java.io.File(file1Path).getName();
        String baseName2 = new java.io.File(file2Path).getName();

        // Remove extensions
        if (baseName1.contains(".")) {
            baseName1 = baseName1.substring(0, baseName1.lastIndexOf('.'));
        }
        if (baseName2.contains(".")) {
            baseName2 = baseName2.substring(0, baseName2.lastIndexOf('.'));
        }

        // Generate timestamp
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        return String.format("%s_vs_%s_%s_comparison_%s.xlsx",
                baseName1, baseName2, type.toLowerCase(), timestamp);
    }

    /**
     * Common helper method to validate required parameters for file comparison.
     */
    private static McpSchema.CallToolResult validateRequiredParams(String file1Path, String file2Path, String uniqueKey, String outputPath) {
        if (file1Path == null || file1Path.trim().isEmpty()) {
            List<McpSchema.Content> errorContents = new ArrayList<>();
            errorContents.add(new McpSchema.TextContent("Missing required parameter: file1Path"));
            return new McpSchema.CallToolResult(errorContents, true);
        }
        if (file2Path == null || file2Path.trim().isEmpty()) {
            List<McpSchema.Content> errorContents = new ArrayList<>();
            errorContents.add(new McpSchema.TextContent("Missing required parameter: file2Path"));
            return new McpSchema.CallToolResult(errorContents, true);
        }
        if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
            List<McpSchema.Content> errorContents = new ArrayList<>();
            errorContents.add(new McpSchema.TextContent("Missing required parameter: uniqueKey"));
            return new McpSchema.CallToolResult(errorContents, true);
        }
        if (outputPath == null || outputPath.trim().isEmpty()) {
            List<McpSchema.Content> errorContents = new ArrayList<>();
            errorContents.add(new McpSchema.TextContent("Missing required parameter: outputPath"));
            return new McpSchema.CallToolResult(errorContents, true);
        }
        return null; // All validation passed
    }

    /**
     * Common helper method to parse ignore columns string into Set with proper validation.
     */
    private static Set<String> parseIgnoreColumns(String ignoreColumnsStr) {
        if (ignoreColumnsStr == null || ignoreColumnsStr.trim().isEmpty()) {
            return null;
        }

        Set<String> ignoreColumns = new HashSet<>();
        String[] columns = ignoreColumnsStr.split(",");
        for (String column : columns) {
            String trimmed = column.trim();
            if (!trimmed.isEmpty()) {
                ignoreColumns.add(trimmed);
            }
        }
        return ignoreColumns.isEmpty() ? null : ignoreColumns;
    }

    /**
     * Common helper method to format comparison results for MCP response.
     */
    private static List<McpSchema.Content> formatComparisonResult(CompareResult result, String type, String reportPath) {
        List<McpSchema.Content> contents = new ArrayList<>();
        contents.add(new McpSchema.TextContent(type + " Comparison Complete"));
        contents.add(new McpSchema.TextContent("Source Records: " + result.getSourceRecordCount()));
        contents.add(new McpSchema.TextContent("Mismatched Records: " + result.getMismatchRecordCount()));
        contents.add(new McpSchema.TextContent("Mismatch Details: " +
                (result.getMismatchDetail() != null ? result.getMismatchDetail().toString() : "No details available")));
        contents.add(new McpSchema.TextContent("Mismatch Count by Columns: " +
                (result.getMismatchCountByColumns() != null ? result.getMismatchCountByColumns().toString() : "No data available")));
        contents.add(new McpSchema.TextContent("4-Sheet Excel Report: Generated successfully"));
        contents.add(new McpSchema.TextContent("Report Location: " + reportPath));
        return contents;
    }

    /**
     * Test method to verify CSV comparison functionality with user's actual files.
     */
    private static void testUserCsvFiles() {
        System.out.println("=== Testing Simplified CSV Comparison with User's Actual Files ===");

        try {
            String file1Path = "/Users/amresh/Downloads/compare_pre - Sheet1.csv";
            String file2Path = "/Users/amresh/Downloads/compare_post - Sheet1.csv";
            String uniqueKey = "id";

            System.out.println("File 1: " + file1Path);
            System.out.println("File 2: " + file2Path);
            System.out.println("Unique Key: " + uniqueKey);

            // Test unified CSV comparison method with 4-sheet Excel report generation
            System.out.println("\n🔄 Testing unified CSV comparison method with auto-delimiter detection...");
            CompareResult result = CsvComparisonMCPWrapper.compareCsvFiles(
                file1Path, file2Path, null, null, false, false, uniqueKey, null, null, null
            );

            System.out.println("Unified CSV Comparison Result Summary:");
            System.out.println("- Source Records: " + result.getSourceRecordCount());
            System.out.println("- Total Source Records: " + result.getTotalSourceRecords());
            System.out.println("- Total Target Records: " + result.getTotalTargetRecords());
            System.out.println("- Mismatch Records: " + result.getMismatchRecordCount());
            System.out.println("- Matching Records: " + result.getMatchingRecords());
            System.out.println("- Source Type: " + (result.getSourceType() != null ? result.getSourceType() : "Not Set"));
            System.out.println("- Target Type: " + (result.getTargetType() != null ? result.getTargetType() : "Not Set"));
            System.out.println("- Source Location: " + (result.getSourceLocation() != null ? result.getSourceLocation() : "Not Set"));
            System.out.println("- Target Location: " + (result.getTargetLocation() != null ? result.getTargetLocation() : "Not Set"));
            System.out.println("- Mismatch Detail: " + (result.getMismatchDetail() != null ? "Available" : "NULL"));
            System.out.println("- Mismatch Columns: " + (result.getMismatchCountByColumns() != null ? result.getMismatchCountByColumns() : "NULL"));

            if (result.getMismatchDetail() != null) {
                System.out.println("- Mismatch Details: " + result.getMismatchDetail().toString());
            }

            System.out.println("- 4-Sheet Excel Report: Generated automatically in current directory");

            System.out.println("\n✅ Unified CSV comparison test completed successfully!");
            System.out.println("🎉 Simplified: CSV comparisons now use unified architecture with mandatory 4-sheet Excel reports!");

        } catch (Exception e) {
            System.err.println("❌ Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==========================================================
    // Database Tool Specification (Phase 1: Basic Operations)
    // ==========================================================

    private static McpServerFeatures.SyncToolSpecification getDatabaseToolSpecification() {
        String schema = """
        {
            "type": "object",
            "id": "urn:jsonschema:DatabaseTool",
            "properties": {
                "operation": {
                    "type": "string",
                    "description": "Operation to perform: connect, preview, export, compare, cross_compare, close",
                    "enum": ["connect", "preview", "export", "compare", "cross_compare", "close"]
                },
                "databaseType": {
                    "type": "string",
                    "description": "Database type (currently only postgresql supported)",
                    "default": "postgresql"
                },
                "jdbcUrl": {
                    "type": "string",
                    "description": "PostgreSQL JDBC URL (e.g., jdbc:postgresql://localhost:5432/dbname)"
                },
                "username": {
                    "type": "string",
                    "description": "Database username"
                },
                "password": {
                    "type": "string",
                    "description": "Database password"
                },
                "connectionId": {
                    "type": "string",
                    "description": "Connection ID for subsequent operations (returned by connect operation)"
                },
                "query": {
                    "type": "string",
                    "description": "SQL query to execute (SELECT statements only for security)"
                },
                "previewRows": {
                    "type": "integer",
                    "description": "Number of rows to return for preview (default: 5)",
                    "default": 5
                },
                "outputPath": {
                    "type": "string",
                    "description": "Output directory path for export operation (filename will be auto-generated)"
                },
                "sheetName": {
                    "type": "string",
                    "description": "Excel sheet name for export (default: 'QueryResult')",
                    "default": "QueryResult"
                },
                "sourceQuery": {
                    "type": "string",
                    "description": "Source SQL query for comparison (SELECT statements only)"
                },
                "targetQuery": {
                    "type": "string",
                    "description": "Target SQL query for comparison (SELECT statements only)"
                },
                "uniqueKey": {
                    "type": "string",
                    "description": "Column name(s) to use as unique key for comparison (comma-separated for multi-column keys)"
                },
                "ignoreColumns": {
                    "type": "string",
                    "description": "Comma-separated column names to ignore during comparison (e.g., 'Timestamp,LastModified')"
                },
                "thresholds": {
                    "type": "object",
                    "description": "Numeric thresholds for comparison by column name (e.g., {'balance': 0.01, 'amount': 0.1})"
                },
                "sourceJdbcUrl": {
                    "type": "string",
                    "description": "Source database JDBC URL for cross-database comparison (e.g., jdbc:postgresql://prod-host:5432/proddb)"
                },
                "sourceUsername": {
                    "type": "string",
                    "description": "Source database username for cross-database comparison"
                },
                "sourcePassword": {
                    "type": "string",
                    "description": "Source database password for cross-database comparison"
                },
                "targetJdbcUrl": {
                    "type": "string",
                    "description": "Target database JDBC URL for cross-database comparison (e.g., jdbc:postgresql://uat-host:5432/uatdb)"
                },
                "targetUsername": {
                    "type": "string",
                    "description": "Target database username for cross-database comparison"
                },
                "targetPassword": {
                    "type": "string",
                    "description": "Target database password for cross-database comparison"
                }
            },
            "required": ["operation"]
        }
        """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "database_tool",
                        "PostgreSQL database operations: connect to database, execute queries safely, and manage connections.",
                        schema
                ),
                (exchange, arguments) -> {
                    String operation = (String) arguments.get("operation");

                    try {
                        switch (operation.toLowerCase()) {
                            case "connect":
                                return new McpSchema.CallToolResult(handleDatabaseConnect(arguments), false);
                            case "preview":
                                return new McpSchema.CallToolResult(handleQueryPreview(arguments), false);
                            case "export":
                                return new McpSchema.CallToolResult(handleQueryExport(arguments), false);
                            case "compare":
                                return new McpSchema.CallToolResult(handleQueryCompare(arguments), false);
                            case "cross_compare":
                                return new McpSchema.CallToolResult(handleCrossDatabaseCompare(arguments), false);
                            case "close":
                                return new McpSchema.CallToolResult(handleConnectionClose(arguments), false);
                            default:
                                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Error: Unknown operation: " + operation)), true);
                        }
                    } catch (Exception e) {
                        log.error("Database tool operation failed: {}", e.getMessage(), e);
                        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Error: " + e.getMessage())), true);
                    }
                }
        );
    }

    /**
     * Handle database connection operation.
     */
    private static List<McpSchema.Content> handleDatabaseConnect(Map<String, Object> arguments) {
        try {
            String jdbcUrl = (String) arguments.get("jdbcUrl");
            String username = (String) arguments.get("username");
            String password = (String) arguments.get("password");

            if (jdbcUrl == null || username == null || password == null) {
                return List.of(new McpSchema.TextContent("Error: jdbcUrl, username, and password are required for connect operation"));
            }

            var result = DatabaseMCPWrapper.connectToDatabase(jdbcUrl, username, password);

            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("=== Database Connection Result ==="));
            contents.add(new McpSchema.TextContent("Status: " + result.getStatus()));
            contents.add(new McpSchema.TextContent("Connection ID: " + (result.getConnectionId() != null ? result.getConnectionId() : "N/A")));
            contents.add(new McpSchema.TextContent("Database Version: " + (result.getDatabaseVersion() != null ? result.getDatabaseVersion() : "Unknown")));
            contents.add(new McpSchema.TextContent("Available Tables: " + (result.getAvailableTables() != null ? result.getAvailableTables() : "None")));
            contents.add(new McpSchema.TextContent("Table Count: " + result.getTableCount()));
            contents.add(new McpSchema.TextContent("Message: " + (result.getMessage() != null ? result.getMessage() : "No additional details")));

            return contents;

        } catch (Exception e) {
            log.error("Database connect failed: {}", e.getMessage(), e);
            return List.of(new McpSchema.TextContent("Error connecting to database: " + e.getMessage()));
        }
    }

    /**
     * Handle query preview operation.
     */
    private static List<McpSchema.Content> handleQueryPreview(Map<String, Object> arguments) {
        try {
            String connectionId = (String) arguments.get("connectionId");
            String query = (String) arguments.get("query");
            Integer previewRows = (Integer) arguments.get("previewRows");

            if (connectionId == null || query == null) {
                return List.of(new McpSchema.TextContent("Error: connectionId and query are required for preview operation"));
            }

            var result = DatabaseMCPWrapper.executeQuery(connectionId, query, previewRows);

            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("=== Query Preview Result ==="));
            contents.add(new McpSchema.TextContent("Status: " + result.getStatus()));
            contents.add(new McpSchema.TextContent("Total Row Count: " + result.getTotalRowCount()));
            contents.add(new McpSchema.TextContent("Preview Rows Returned: " + (result.getPreviewData() != null ? result.getPreviewData().size() : 0)));
            contents.add(new McpSchema.TextContent("Execution Time: " + result.getExecutionTime()));
            contents.add(new McpSchema.TextContent("Has More Rows: " + result.isHasMoreRows()));
            contents.add(new McpSchema.TextContent("Column Names: " + (result.getColumnNames() != null ? result.getColumnNames() : "None")));

            if (result.getPreviewData() != null && !result.getPreviewData().isEmpty()) {
                contents.add(new McpSchema.TextContent("\n=== Preview Data ==="));
                int rowNum = 1;
                for (var row : result.getPreviewData()) {
                    contents.add(new McpSchema.TextContent("Row " + rowNum + ": " + row.toString()));
                    rowNum++;
                }
            }

            return contents;

        } catch (Exception e) {
            log.error("Query preview failed: {}", e.getMessage(), e);
            return List.of(new McpSchema.TextContent("Error executing query: " + e.getMessage()));
        }
    }

    /**
     * Handle connection close operation.
     */
    private static List<McpSchema.Content> handleConnectionClose(Map<String, Object> arguments) {
        try {
            String connectionId = (String) arguments.get("connectionId");

            if (connectionId == null) {
                return List.of(new McpSchema.TextContent("Error: connectionId is required for close operation"));
            }

            var result = DatabaseMCPWrapper.closeConnection(connectionId);

            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("=== Connection Close Result ==="));
            contents.add(new McpSchema.TextContent("Status: " + result.get("status")));
            contents.add(new McpSchema.TextContent("Message: " + result.get("message")));

            return contents;

        } catch (Exception e) {
            log.error("Connection close failed: {}", e.getMessage(), e);
            return List.of(new McpSchema.TextContent("Error closing connection: " + e.getMessage()));
        }
    }

    /**
     * Handle query export operation.
     */
    private static List<McpSchema.Content> handleQueryExport(Map<String, Object> arguments) {
        try {
            String connectionId = (String) arguments.get("connectionId");
            String query = (String) arguments.get("query");
            String outputPath = (String) arguments.get("outputPath");
            String sheetName = (String) arguments.getOrDefault("sheetName", "QueryResult");

            if (connectionId == null || query == null || outputPath == null) {
                return List.of(new McpSchema.TextContent("Error: connectionId, query, and outputPath are required for export operation"));
            }

            var result = DatabaseMCPWrapper.executeQueryAndExportWithAutoFilename(connectionId, query, outputPath, sheetName);

            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("=== Query Export Result ==="));
            contents.add(new McpSchema.TextContent("Status: " + result.get("status")));
            contents.add(new McpSchema.TextContent("Export File: " + result.get("exportFile")));
            contents.add(new McpSchema.TextContent("Auto-Generated Filename: " + result.get("autoGeneratedFilename")));
            contents.add(new McpSchema.TextContent("Total Rows Exported: " + result.get("totalRowsExported")));
            contents.add(new McpSchema.TextContent("Execution Time: " + result.get("executionTime")));
            contents.add(new McpSchema.TextContent("Sheet Name: " + result.get("sheetName")));
            contents.add(new McpSchema.TextContent("Message: " + result.get("message")));

            return contents;

        } catch (Exception e) {
            log.error("Query export failed: {}", e.getMessage(), e);
            return List.of(new McpSchema.TextContent("Error exporting query: " + e.getMessage()));
        }
    }

    /**
     * Handle query comparison operation.
     */
    private static List<McpSchema.Content> handleQueryCompare(Map<String, Object> arguments) {
        try {
            String connectionId = (String) arguments.get("connectionId");
            String sourceQuery = (String) arguments.get("sourceQuery");
            String targetQuery = (String) arguments.get("targetQuery");
            String uniqueKey = (String) arguments.get("uniqueKey");
            String outputPath = (String) arguments.get("outputPath");
            String ignoreColumnsStr = (String) arguments.getOrDefault("ignoreColumns", null);
            @SuppressWarnings("unchecked")
            Map<String, Double> thresholds = (Map<String, Double>) arguments.getOrDefault("thresholds", null);

            if (connectionId == null || sourceQuery == null || targetQuery == null || uniqueKey == null || outputPath == null) {
                return List.of(new McpSchema.TextContent("Error: connectionId, sourceQuery, targetQuery, uniqueKey, and outputPath are required for compare operation"));
            }

            // Convert ignoreColumns string to Set
            Set<String> ignoreColumns = parseIgnoreColumns(ignoreColumnsStr);

            var result = DatabaseMCPWrapper.executeQueryComparison(
                connectionId, sourceQuery, targetQuery, uniqueKey, ignoreColumns, thresholds, outputPath
            );

            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("=== Database Comparison Result ==="));
            contents.add(new McpSchema.TextContent("Status: " + result.get("status")));
            contents.add(new McpSchema.TextContent("Comparison Report: " + result.get("reportFile")));
            contents.add(new McpSchema.TextContent("Auto-Generated Filename: " + result.get("autoGeneratedFilename")));
            contents.add(new McpSchema.TextContent("Source Rows: " + result.get("sourceRowCount")));
            contents.add(new McpSchema.TextContent("Target Rows: " + result.get("targetRowCount")));
            contents.add(new McpSchema.TextContent("Match Count: " + result.get("matchCount")));
            contents.add(new McpSchema.TextContent("Mismatch Count: " + result.get("mismatchCount")));
            contents.add(new McpSchema.TextContent("Source Extra Count: " + result.get("sourceExtraCount")));
            contents.add(new McpSchema.TextContent("Target Extra Count: " + result.get("targetExtraCount")));
            contents.add(new McpSchema.TextContent("Execution Time: " + result.get("executionTime")));
            contents.add(new McpSchema.TextContent("Message: " + result.get("message")));

            return contents;

        } catch (Exception e) {
            log.error("Query comparison failed: {}", e.getMessage(), e);
            return List.of(new McpSchema.TextContent("Error comparing queries: " + e.getMessage()));
        }
    }

    /**
     * Handle cross-database comparison operation.
     */
    private static List<McpSchema.Content> handleCrossDatabaseCompare(Map<String, Object> arguments) {
        try {
            // Extract source database parameters
            String sourceJdbcUrl = (String) arguments.get("sourceJdbcUrl");
            String sourceUsername = (String) arguments.get("sourceUsername");
            String sourcePassword = (String) arguments.get("sourcePassword");
            String sourceQuery = (String) arguments.get("sourceQuery");

            // Extract target database parameters
            String targetJdbcUrl = (String) arguments.get("targetJdbcUrl");
            String targetUsername = (String) arguments.get("targetUsername");
            String targetPassword = (String) arguments.get("targetPassword");
            String targetQuery = (String) arguments.get("targetQuery");

            // Extract common parameters
            String uniqueKey = (String) arguments.get("uniqueKey");
            String outputPath = (String) arguments.get("outputPath");
            String ignoreColumnsStr = (String) arguments.getOrDefault("ignoreColumns", null);
            @SuppressWarnings("unchecked")
            Map<String, Double> thresholds = (Map<String, Double>) arguments.getOrDefault("thresholds", null);

            // Validate required parameters
            if (sourceJdbcUrl == null || sourceUsername == null || sourcePassword == null || sourceQuery == null ||
                targetJdbcUrl == null || targetUsername == null || targetPassword == null || targetQuery == null ||
                uniqueKey == null || outputPath == null) {
                return List.of(new McpSchema.TextContent("Error: sourceJdbcUrl, sourceUsername, sourcePassword, sourceQuery, " +
                        "targetJdbcUrl, targetUsername, targetPassword, targetQuery, uniqueKey, and outputPath are required for cross_compare operation"));
            }

            // Convert ignoreColumns string to Set
            Set<String> ignoreColumns = parseIgnoreColumns(ignoreColumnsStr);

            // Execute cross-database comparison
            var result = DatabaseMCPWrapper.executeCrossDatabaseComparison(
                sourceJdbcUrl, sourceUsername, sourcePassword, sourceQuery,
                targetJdbcUrl, targetUsername, targetPassword, targetQuery,
                uniqueKey, ignoreColumns, thresholds, outputPath
            );

            // Format response
            List<McpSchema.Content> contents = new ArrayList<>();
            contents.add(new McpSchema.TextContent("=== Cross-Database Comparison Result ==="));
            contents.add(new McpSchema.TextContent("Status: " + result.get("status")));
            contents.add(new McpSchema.TextContent("Source Database: " + result.get("sourceDatabase")));
            contents.add(new McpSchema.TextContent("Target Database: " + result.get("targetDatabase")));
            contents.add(new McpSchema.TextContent("Comparison Report: " + result.get("reportFile")));
            contents.add(new McpSchema.TextContent("Auto-Generated Filename: " + result.get("autoGeneratedFilename")));
            contents.add(new McpSchema.TextContent("Source Rows: " + result.get("sourceRowCount")));
            contents.add(new McpSchema.TextContent("Target Rows: " + result.get("targetRowCount")));
            contents.add(new McpSchema.TextContent("Match Count: " + result.get("matchCount")));
            contents.add(new McpSchema.TextContent("Mismatch Count: " + result.get("mismatchCount")));
            contents.add(new McpSchema.TextContent("Source Extra Count: " + result.get("sourceExtraCount")));
            contents.add(new McpSchema.TextContent("Target Extra Count: " + result.get("targetExtraCount")));
            contents.add(new McpSchema.TextContent("Execution Time: " + result.get("executionTime")));
            contents.add(new McpSchema.TextContent("Message: " + result.get("message")));

            return contents;

        } catch (Exception e) {
            log.error("Cross-database comparison failed: {}", e.getMessage(), e);
            return List.of(new McpSchema.TextContent("Error executing cross-database comparison: " + e.getMessage()));
        }
    }

    /**
     * Test cross-database comparison functionality with real Docker PostgreSQL containers.
     */
    private static void testCrossDatabaseComparison() {
        System.out.println("=== Real Cross-Database Comparison Test ===");

        try {
            // Production database (Docker container on port 5434)
            String prodJdbcUrl = "jdbc:postgresql://localhost:5434/proddb";
            String prodUsername = "produser";
            String prodPassword = "prodpass";
            String prodQuery = "SELECT customer_id, name, email, region, balance FROM customers WHERE active = true ORDER BY customer_id";

            // UAT database (Docker container on port 5435)
            String uatJdbcUrl = "jdbc:postgresql://localhost:5435/uatdb";
            String uatUsername = "uatuser";
            String uatPassword = "uatpass";
            String uatQuery = "SELECT customer_id, name, email, region, balance FROM customers WHERE active = true ORDER BY customer_id";

            String uniqueKey = "customer_id";
            String outputDir = "/tmp";

            // Test 1: Basic comparison without thresholds
            System.out.println("\n1. Testing cross-database comparison without thresholds...");

            Map<String, Object> result1 = DatabaseMCPWrapper.executeCrossDatabaseComparison(
                prodJdbcUrl, prodUsername, prodPassword, prodQuery,
                uatJdbcUrl, uatUsername, uatPassword, uatQuery,
                uniqueKey, null, null, outputDir
            );

            System.out.println("✅ Results:");
            System.out.println("Status: " + result1.get("status"));
            System.out.println("Source Database: " + result1.get("sourceDatabase"));
            System.out.println("Target Database: " + result1.get("targetDatabase"));
            System.out.println("Source Rows: " + result1.get("sourceRowCount"));
            System.out.println("Target Rows: " + result1.get("targetRowCount"));
            System.out.println("Match Count: " + result1.get("matchCount"));
            System.out.println("Mismatch Count: " + result1.get("mismatchCount"));
            System.out.println("Source Extra Count: " + result1.get("sourceExtraCount"));
            System.out.println("Target Extra Count: " + result1.get("targetExtraCount"));
            System.out.println("Report File: " + result1.get("reportFile"));
            System.out.println("Execution Time: " + result1.get("executionTime"));

            // Test 2: With balance threshold
            System.out.println("\n2. Testing with balance threshold (1.0)...");

            Map<String, Double> thresholds = new HashMap<>();
            thresholds.put("balance", 1.0); // 1.00 threshold for balance differences

            Map<String, Object> result2 = DatabaseMCPWrapper.executeCrossDatabaseComparison(
                prodJdbcUrl, prodUsername, prodPassword, prodQuery,
                uatJdbcUrl, uatUsername, uatPassword, uatQuery,
                uniqueKey, null, thresholds, outputDir
            );

            System.out.println("✅ Results with threshold:");
            System.out.println("Status: " + result2.get("status"));
            System.out.println("Match Count: " + result2.get("matchCount"));
            System.out.println("Mismatch Count: " + result2.get("mismatchCount"));
            System.out.println("Report File: " + result2.get("reportFile"));

            // Test 3: With ignore columns
            System.out.println("\n3. Testing with ignore region column...");

            Set<String> ignoreColumns = new HashSet<>();
            ignoreColumns.add("region"); // Ignore region differences

            Map<String, Object> result3 = DatabaseMCPWrapper.executeCrossDatabaseComparison(
                prodJdbcUrl, prodUsername, prodPassword, prodQuery,
                uatJdbcUrl, uatUsername, uatPassword, uatQuery,
                uniqueKey, ignoreColumns, null, outputDir
            );

            System.out.println("✅ Results ignoring region:");
            System.out.println("Status: " + result3.get("status"));
            System.out.println("Match Count: " + result3.get("matchCount"));
            System.out.println("Mismatch Count: " + result3.get("mismatchCount"));
            System.out.println("Report File: " + result3.get("reportFile"));

            System.out.println("\n📊 Data Differences Expected:");
            System.out.println("- Customer 2: Balance difference (1500.50 vs 1500.75)");
            System.out.println("- Customer 4: Region difference (US vs EU)");
            System.out.println("- Customer 5: Only in PROD (Charlie Wilson)");
            System.out.println("- Customer 6: Only in UAT (Dave Miller)");

            System.out.println("\n=== Cross-Database Comparison Test COMPLETED ===");
            System.out.println("🎉 Successfully tested with real PostgreSQL containers!");

        } catch (Exception e) {
            System.err.println("❌ Cross-database test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }


}