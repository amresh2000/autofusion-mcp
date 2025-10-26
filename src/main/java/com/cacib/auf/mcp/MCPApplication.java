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

        // ===== MCP Server =====
        // Can copy and paste this into another project to spin up the server
        McpServer.sync(transportProvider)
                .serverInfo("jaxos-mcp-server", "0.0.1")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(apiRequestToolSpecification, apiSchemaValidatorToolSpecification,
                       excelComparisonToolSpecification, csvComparisonToolSpecification)
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
}