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
        // ===== Stdio Server Transport =====
        var transportProvider = new StdioServerTransportProvider(new ObjectMapper());

        // ===== API Request Tool Specification =====
        var apiRequestToolSpecification = getApiRequestToolSpecification();

        // ===== API Schema Validator Tool Specification =====
        var apiSchemaValidatorToolSpecification = getApiSchemaValidatorToolSpecification();

        // ===== Excel Comparison Tool Specification =====
        var excelComparisonToolSpecification = getExcelComparisonToolSpecification();

        // ===== CSV Comparison Tool Specification =====
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
                "outputPath": { "type": "string" },
                "sheetName": { "type": "string" },
                "compareMode": {
                    "type": "string",
                    "enum": ["cell_by_cell", "row_difference"]
                },
                "headerRow": {
                    "type": "integer",
                    "description": "Row number containing headers (default: 1)",
                    "minimum": 1
                },
                "dataStartRow": {
                    "type": "integer",
                    "description": "First row containing data (default: 2)",
                    "minimum": 1
                },
                "keyColumns": {
                    "type": "string",
                    "description": "Comma-separated key columns for matching (e.g., 'A,B' or 'Account,Department')"
                },
                "ignoredSheets": {
                    "type": "string",
                    "description": "Comma-separated sheet names to ignore during comparison"
                },
                "outputDir": {
                    "type": "string",
                    "description": "Custom output directory for comparison results"
                },
                "outputFileName": {
                    "type": "string",
                    "description": "Custom output file name for comparison results"
                }
            },
            "required": ["file1Path", "file2Path"]
        }
        """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "excel_compare",
                        "Compare two Excel files with advanced configuration options including custom header rows, key columns, and sheet exclusion. Supports both basic and config-based comparison modes.",
                        schema
                ),
                (exchange, arguments) -> {
                    String file1Path = (String) arguments.get("file1Path");
                    String file2Path = (String) arguments.get("file2Path");
                    String outputPath = (String) arguments.getOrDefault("outputPath", null);
                    String sheetName = (String) arguments.getOrDefault("sheetName", null);
                    String compareMode = (String) arguments.getOrDefault("compareMode", "row_difference");
                    Integer headerRow = (Integer) arguments.getOrDefault("headerRow", null);
                    Integer dataStartRow = (Integer) arguments.getOrDefault("dataStartRow", null);
                    String keyColumns = (String) arguments.getOrDefault("keyColumns", null);
                    String ignoredSheets = (String) arguments.getOrDefault("ignoredSheets", null);
                    String outputDir = (String) arguments.getOrDefault("outputDir", null);
                    String outputFileName = (String) arguments.getOrDefault("outputFileName", null);

                    try {
                        CompareResult result;

                        // Smart routing: Use config-based method if advanced parameters are provided
                        if (headerRow != null || dataStartRow != null ||
                            (keyColumns != null && !keyColumns.trim().isEmpty()) ||
                            (ignoredSheets != null && !ignoredSheets.trim().isEmpty()) ||
                            (outputDir != null && !outputDir.trim().isEmpty()) ||
                            (outputFileName != null && !outputFileName.trim().isEmpty())) {

                            log.info("Using Excel comparison with config - advanced parameters detected");
                            result = ExcelComparisonMCPWrapper.compareExcelFilesWithConfig(
                                    file1Path, file2Path, outputPath, sheetName, compareMode,
                                    headerRow, dataStartRow, keyColumns, ignoredSheets, outputDir, outputFileName);
                        } else {
                            log.info("Using Excel comparison basic mode - backwards compatibility");
                            result = ExcelComparisonMCPWrapper.compareExcelFiles(
                                    file1Path, file2Path, outputPath, sheetName, compareMode);
                        }

                        List<McpSchema.Content> contents = new ArrayList<>();
                        contents.add(new McpSchema.TextContent("Comparison Mode: " + compareMode));
                        contents.add(new McpSchema.TextContent("Mismatched Records: " + result.getMismatchRecordCount()));
                        contents.add(new McpSchema.TextContent("Source Records: " + result.getSourceRecordCount()));
                        contents.add(new McpSchema.TextContent("Mismatch Details: " + result.getMismatchDetail().toString()));
                        contents.add(new McpSchema.TextContent("Mismatch Count by Columns: " + result.getMismatchCountByColumns().toString()));

                        return new McpSchema.CallToolResult(contents, false);

                    } catch (Exception e) {
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
                "delimiter1": { "type": "string" },
                "delimiter2": { "type": "string" },
                "skipHeader1": { "type": "boolean" },
                "skipHeader2": { "type": "boolean" },
                "uniqueKeyColumn": { "type": "string" },
                "thresholds": { "type": "object" },
                "customUniqueKey": {
                    "type": "string",
                    "description": "Custom unique key column name (overrides uniqueKeyColumn)"
                },
                "ignoreColumns": {
                    "type": "string",
                    "description": "CSV string of columns to ignore during comparison (e.g., 'password,ssn,credit_card')"
                }
            },
            "required": ["file1Path", "file2Path"]
        }
        """;

        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "csv_compare",
                        "Compare two CSV files with configurable delimiters, header handling, numeric thresholds, custom unique keys, and column exclusion. Supports both V1 and V2 comparison engines.",
                        schema
                ),
                (exchange, arguments) -> {
                    String file1Path = (String) arguments.get("file1Path");
                    String file2Path = (String) arguments.get("file2Path");
                    String delimiter1 = (String) arguments.getOrDefault("delimiter1", null);
                    String delimiter2 = (String) arguments.getOrDefault("delimiter2", null);
                    Boolean skipHeader1 = (Boolean) arguments.getOrDefault("skipHeader1", null);
                    Boolean skipHeader2 = (Boolean) arguments.getOrDefault("skipHeader2", null);
                    String uniqueKeyColumn = (String) arguments.getOrDefault("uniqueKeyColumn", null);
                    @SuppressWarnings("unchecked")
                    Map<String, Double> thresholds = (Map<String, Double>) arguments.getOrDefault("thresholds", null);
                    String customUniqueKey = (String) arguments.getOrDefault("customUniqueKey", null);
                    String ignoreColumns = (String) arguments.getOrDefault("ignoreColumns", null);

                    try {
                        CompareResult result;

                        // Smart routing: Use V2 if enhanced parameters are provided
                        if ((customUniqueKey != null && !customUniqueKey.trim().isEmpty()) ||
                            (ignoreColumns != null && !ignoreColumns.trim().isEmpty())) {

                            log.info("Using CSV comparison V2 - enhanced parameters detected");
                            result = CsvComparisonMCPWrapper.compareCsvFilesV2(
                                    file1Path, file2Path, delimiter1, delimiter2,
                                    skipHeader1, skipHeader2, uniqueKeyColumn, thresholds,
                                    customUniqueKey, ignoreColumns);
                        } else {
                            log.info("Using CSV comparison V1 - backwards compatibility mode");
                            result = CsvComparisonMCPWrapper.compareCsvFiles(
                                    file1Path, file2Path, delimiter1, delimiter2,
                                    skipHeader1, skipHeader2, uniqueKeyColumn, thresholds);
                        }

                        List<McpSchema.Content> contents = new ArrayList<>();
                        contents.add(new McpSchema.TextContent("Mismatched Records: " + result.getMismatchRecordCount()));
                        contents.add(new McpSchema.TextContent("Source Records: " + result.getSourceRecordCount()));
                        contents.add(new McpSchema.TextContent("Mismatch Details: " + result.getMismatchDetail().toString()));
                        contents.add(new McpSchema.TextContent("Mismatch Count by Columns: " + result.getMismatchCountByColumns().toString()));

                        return new McpSchema.CallToolResult(contents, false);

                    } catch (Exception e) {
                        List<McpSchema.Content> errorContents = new ArrayList<>();
                        errorContents.add(new McpSchema.TextContent("Error: " + e.getMessage()));
                        return new McpSchema.CallToolResult(errorContents, true);
                    }
                }
        );
    }
}