package com.cacib.auf.mcp;

import com.cacib.auf.core.database.connection.DatabaseConnectionManager;
import com.cacib.auf.core.database.model.ConnectionResult;
import com.cacib.auf.core.database.model.QueryResult;
import com.cacib.auf.core.database.resultset.AfResultSetHelper;
import com.cacib.auf.core.comparison.table.AfTableComparisonCTRLV2;
import com.cacib.auf.core.comparison.model.CompareResult;
import com.cacib.auf.core.comparison.excel.ExcelReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP wrapper for database operations.
 * Provides PostgreSQL database connectivity, query execution, and comparison capabilities.
 * Follows the same pattern as ExcelComparisonMCPWrapper and CsvComparisonMCPWrapper.
 */
public class DatabaseMCPWrapper {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMCPWrapper.class);


    // Dangerous SQL keywords that should be blocked
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
        "DROP", "DELETE", "INSERT", "UPDATE", "ALTER", "CREATE",
        "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE"
    );

    /**
     * Connect to PostgreSQL database.
     *
     * @param jdbcUrl PostgreSQL JDBC URL (e.g., jdbc:postgresql://localhost:5432/dbname)
     * @param username Database username
     * @param password Database password
     * @return ConnectionResult with connection ID and metadata
     */
    public static ConnectionResult connectToDatabase(String jdbcUrl, String username, String password) {
        try {
            log.info("Attempting to connect to database: {}", jdbcUrl);

            // Validate parameters
            validateConnectionParameters(jdbcUrl, username, password);

            // Attempt connection using DatabaseConnectionManager
            ConnectionResult result = DatabaseConnectionManager.connect(jdbcUrl, username, password);

            if ("CONNECTED".equals(result.getStatus())) {
                log.info("Database connection successful: {}", result.getConnectionId());
            } else {
                log.error("Database connection failed: {}", result.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("Database connection error: {}", e.getMessage(), e);
            return new ConnectionResult(null, "FAILED", "Connection failed: " + e.getMessage());
        }
    }

    /**
     * Execute query and return preview of results.
     *
     * @param connectionId Connection ID from previous connect operation
     * @param query SQL query to execute (SELECT only)
     * @param previewRows Number of rows to return for preview (default: 5)
     * @return QueryResult with preview data and metadata
     * @throws Exception If query execution fails
     */
    public static QueryResult executeQuery(String connectionId, String query, Integer previewRows) throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Executing query for connection: {}", connectionId);

            // Validate parameters
            if (connectionId == null || connectionId.trim().isEmpty()) {
                throw new Exception("Connection ID is required");
            }
            if (query == null || query.trim().isEmpty()) {
                throw new Exception("Query is required");
            }

            // Set default preview rows
            int finalPreviewRows = previewRows != null ? previewRows : 5;
            if (finalPreviewRows <= 0) finalPreviewRows = 5;

            // Validate query safety
            validateQuerySafety(query);

            // Get database connection
            Connection conn = DatabaseConnectionManager.getConnection(connectionId);

            // Add LIMIT clause for preview
            String limitedQuery = addLimitToQuery(query, finalPreviewRows);

            // Execute limited query for preview data
            List<LinkedHashMap<String, String>> previewData = getResultsetAsListOfMap(conn, limitedQuery);

            // Get total row count with separate query
            int totalRowCount = getTotalRowCount(conn, query);

            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;
            String executionTimeStr = executionTime + "ms";

            log.info("Query executed successfully. Preview rows: {}, Total rows: {}, Execution time: {}",
                    previewData.size(), totalRowCount, executionTimeStr);

            return new QueryResult(previewData, totalRowCount, executionTimeStr);

        } catch (Exception e) {
            log.error("Query execution failed for connection {}: {}", connectionId, e.getMessage(), e);
            throw new Exception("Query execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Close database connection.
     *
     * @param connectionId Connection ID to close
     * @return Status map indicating success or failure
     */
    public static Map<String, String> closeConnection(String connectionId) {
        Map<String, String> result = new HashMap<>();

        try {
            if (connectionId == null || connectionId.trim().isEmpty()) {
                result.put("status", "FAILED");
                result.put("message", "Connection ID is required");
                return result;
            }

            DatabaseConnectionManager.closeConnection(connectionId);

            result.put("status", "CLOSED");
            result.put("message", "Connection " + connectionId + " closed successfully");

            log.info("Connection closed: {}", connectionId);

        } catch (Exception e) {
            log.error("Failed to close connection {}: {}", connectionId, e.getMessage());
            result.put("status", "FAILED");
            result.put("message", "Failed to close connection: " + e.getMessage());
        }

        return result;
    }

    /**
     * Execute query and export results to Excel file.
     *
     * @param connectionId Connection ID from previous connect operation
     * @param query SQL query to execute (SELECT only)
     * @param exportPath Full path where Excel file should be saved
     * @param sheetName Name of the Excel sheet
     * @return Status map with export results
     * @throws Exception If export operation fails
     */
    public static Map<String, Object> executeQueryAndExport(String connectionId, String query, String exportPath, String sheetName) throws Exception {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Executing query and exporting to Excel: {}", exportPath);

            // Validate parameters
            if (connectionId == null || connectionId.trim().isEmpty()) {
                throw new Exception("Connection ID is required");
            }
            if (query == null || query.trim().isEmpty()) {
                throw new Exception("Query is required");
            }
            if (exportPath == null || exportPath.trim().isEmpty()) {
                throw new Exception("Export path is required");
            }

            // Validate query safety
            validateQuerySafety(query);

            // Get database connection
            Connection conn = DatabaseConnectionManager.getConnection(connectionId);

            // Execute query to get all data (no LIMIT for export)
            List<LinkedHashMap<String, String>> allData = getResultsetAsListOfMap(conn, query);

            // Create Excel file
            createExcelFile(allData, exportPath, sheetName);

            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;

            result.put("status", "SUCCESS");
            result.put("exportFile", exportPath);
            result.put("totalRowsExported", allData.size());
            result.put("executionTime", executionTime + "ms");
            result.put("sheetName", sheetName);
            result.put("message", "Query executed and exported successfully");

            log.info("Query exported successfully. Rows: {}, File: {}, Time: {}ms",
                    allData.size(), exportPath, executionTime);

            return result;

        } catch (Exception e) {
            log.error("Query export failed for connection {}: {}", connectionId, e.getMessage(), e);
            result.put("status", "FAILED");
            result.put("exportFile", exportPath);
            result.put("totalRowsExported", 0);
            result.put("executionTime", (System.currentTimeMillis() - startTime) + "ms");
            result.put("sheetName", sheetName);
            result.put("message", "Export failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Execute query and export results to Excel file with auto-generated filename.
     *
     * @param connectionId Connection ID from previous connect operation
     * @param query SQL query to execute (SELECT only)
     * @param outputDir Directory where Excel file should be saved
     * @param sheetName Name of the Excel sheet
     * @return Status map with export results including generated filename
     * @throws Exception If export operation fails
     */
    public static Map<String, Object> executeQueryAndExportWithAutoFilename(String connectionId, String query, String outputDir, String sheetName) throws Exception {
        // Generate auto filename
        String autoFilename = generateQueryExportFilename(query);
        String fullPath = java.nio.file.Paths.get(outputDir, autoFilename).toString();

        // Call the regular export method with full path
        Map<String, Object> result = executeQueryAndExport(connectionId, query, fullPath, sheetName);

        // Update the result to include the auto-generated filename
        result.put("autoGeneratedFilename", autoFilename);

        return result;
    }

    /**
     * Generate auto filename for database query exports.
     */
    private static String generateQueryExportFilename(String query) {
        // Extract first word from query (usually table name or SELECT)
        String queryHint = "query";
        if (query != null && !query.trim().isEmpty()) {
            String[] words = query.trim().split("\\s+");
            if (words.length >= 3 && words[0].toUpperCase().equals("SELECT")) {
                // Try to extract table name from "SELECT ... FROM table_name"
                for (int i = 1; i < words.length; i++) {
                    if (words[i].toUpperCase().equals("FROM") && i + 1 < words.length) {
                        queryHint = words[i + 1].replaceAll("[^a-zA-Z0-9_]", "");
                        break;
                    }
                }
            }
        }

        // Generate timestamp
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        return String.format("database_%s_export_%s.xlsx", queryHint, timestamp);
    }

    /**
     * Execute query comparison between source and target queries with 4-sheet Excel report.
     *
     * @param connectionId Connection ID from previous connect operation
     * @param sourceQuery Source SQL query (SELECT only)
     * @param targetQuery Target SQL query (SELECT only)
     * @param uniqueKey Column name(s) for unique key (comma-separated for multi-column)
     * @param ignoreColumns Set of column names to ignore during comparison
     * @param thresholds Numeric thresholds for comparison by column name
     * @param outputDir Directory where comparison report should be saved
     * @return Status map with comparison results and report location
     * @throws Exception If comparison operation fails
     */
    public static Map<String, Object> executeQueryComparison(String connectionId, String sourceQuery, String targetQuery,
                                                             String uniqueKey, Set<String> ignoreColumns,
                                                             Map<String, Double> thresholds, String outputDir) throws Exception {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Executing database comparison between source and target queries");

            // Validate parameters
            if (connectionId == null || connectionId.trim().isEmpty()) {
                throw new Exception("Connection ID is required");
            }
            if (sourceQuery == null || sourceQuery.trim().isEmpty()) {
                throw new Exception("Source query is required");
            }
            if (targetQuery == null || targetQuery.trim().isEmpty()) {
                throw new Exception("Target query is required");
            }
            if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
                throw new Exception("Unique key is required");
            }
            if (outputDir == null || outputDir.trim().isEmpty()) {
                throw new Exception("Output directory is required");
            }

            // Validate query safety
            validateQuerySafety(sourceQuery);
            validateQuerySafety(targetQuery);

            // Get database connection
            Connection conn = DatabaseConnectionManager.getConnection(connectionId);

            // Execute source and target queries
            List<LinkedHashMap<String, String>> sourceData = getResultsetAsListOfMap(conn, sourceQuery);
            List<LinkedHashMap<String, String>> targetData = getResultsetAsListOfMap(conn, targetQuery);

            // Prepare data for comparison by adding unique key column
            List<Map<String, String>> preparedSourceData = prepareDataForComparison(sourceData, uniqueKey, ignoreColumns);
            List<Map<String, String>> preparedTargetData = prepareDataForComparison(targetData, uniqueKey, ignoreColumns);


            // Perform comparison using AfTableComparisonCTRLV2
            AfTableComparisonCTRLV2 comparator = new AfTableComparisonCTRLV2();
            CompareResult compareResult;

            if (thresholds != null && !thresholds.isEmpty()) {
                compareResult = comparator.getMatchResultWithThreshold(preparedSourceData, preparedTargetData, "UNI_KEY", thresholds);
            } else {
                compareResult = comparator.getMatchResult(preparedSourceData, preparedTargetData, "UNI_KEY");
            }

            // Set metadata for the report
            compareResult.setSourceType("Database Query");
            compareResult.setTargetType("Database Query");
            compareResult.setSourceLocation("Source: " + truncateQuery(sourceQuery));
            compareResult.setTargetLocation("Target: " + truncateQuery(targetQuery));

            // Generate auto filename for comparison report
            String autoFilename = generateComparisonReportFilename(sourceQuery, targetQuery);
            String fullPath = java.nio.file.Paths.get(outputDir, autoFilename).toString();

            // Generate 4-sheet Excel comparison report
            ExcelReportGenerator reportGenerator = new ExcelReportGenerator();
            reportGenerator.generateComparisonReport(compareResult, fullPath);

            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;

            // Build result
            result.put("status", "SUCCESS");
            result.put("reportFile", fullPath);
            result.put("autoGeneratedFilename", autoFilename);
            result.put("sourceRowCount", sourceData.size());
            result.put("targetRowCount", targetData.size());
            result.put("matchCount", compareResult.getMatchingRecords());
            result.put("mismatchCount", compareResult.getMismatchRecordCount());
            result.put("sourceExtraCount", compareResult.getSourceExtraRecords() != null ? compareResult.getSourceExtraRecords().size() : 0);
            result.put("targetExtraCount", compareResult.getTargetExtraRecords() != null ? compareResult.getTargetExtraRecords().size() : 0);
            result.put("executionTime", executionTime + "ms");
            result.put("message", "Database comparison completed successfully");

            log.info("Database comparison completed. Matches: {}, Mismatches: {}, Source Extra: {}, Target Extra: {}, Time: {}ms",
                    compareResult.getMatchingRecords(), compareResult.getMismatchRecordCount(),
                    compareResult.getSourceExtraRecords() != null ? compareResult.getSourceExtraRecords().size() : 0,
                    compareResult.getTargetExtraRecords() != null ? compareResult.getTargetExtraRecords().size() : 0, executionTime);

            return result;

        } catch (Exception e) {
            log.error("Database comparison failed for connection {}: {}", connectionId, e.getMessage(), e);
            result.put("status", "FAILED");
            result.put("reportFile", "");
            result.put("autoGeneratedFilename", "");
            result.put("sourceRowCount", 0);
            result.put("targetRowCount", 0);
            result.put("matchCount", 0);
            result.put("mismatchCount", 0);
            result.put("sourceExtraCount", 0);
            result.put("targetExtraCount", 0);
            result.put("executionTime", (System.currentTimeMillis() - startTime) + "ms");
            result.put("message", "Comparison failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Prepare data for comparison by creating UNI_KEY column and filtering ignored columns.
     */
    private static List<Map<String, String>> prepareDataForComparison(List<LinkedHashMap<String, String>> rawData,
                                                                      String uniqueKey, Set<String> ignoreColumns) {
        List<Map<String, String>> preparedData = new ArrayList<>();

        for (LinkedHashMap<String, String> row : rawData) {
            Map<String, String> preparedRow = new LinkedHashMap<>();

            // Create UNI_KEY from specified unique key column(s)
            String uniKeyValue = createUniqueKeyValue(row, uniqueKey);
            preparedRow.put("UNI_KEY", uniKeyValue);

            // Copy all columns except ignored ones
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String columnName = entry.getKey();
                if (ignoreColumns == null || !ignoreColumns.contains(columnName)) {
                    preparedRow.put(columnName, entry.getValue());
                }
            }

            preparedData.add(preparedRow);
        }

        return preparedData;
    }

    /**
     * Create unique key value from one or more columns.
     */
    private static String createUniqueKeyValue(LinkedHashMap<String, String> row, String uniqueKey) {
        if (uniqueKey.contains(",")) {
            // Multi-column unique key
            String[] keyColumns = uniqueKey.split(",");
            StringBuilder uniKeyBuilder = new StringBuilder();
            for (int i = 0; i < keyColumns.length; i++) {
                String columnName = keyColumns[i].trim();
                String value = row.get(columnName);
                uniKeyBuilder.append(value != null ? value : "");
                if (i < keyColumns.length - 1) {
                    uniKeyBuilder.append("|");
                }
            }
            return uniKeyBuilder.toString();
        } else {
            // Single column unique key
            String value = row.get(uniqueKey.trim());
            return value != null ? value : "";
        }
    }

    /**
     * Generate auto filename for comparison reports.
     */
    private static String generateComparisonReportFilename(String sourceQuery, String targetQuery) {
        String sourceHint = extractTableHint(sourceQuery);
        String targetHint = extractTableHint(targetQuery);

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        return String.format("database_%s_vs_%s_comparison_%s.xlsx", sourceHint, targetHint, timestamp);
    }

    /**
     * Extract table hint from query for filename.
     */
    private static String extractTableHint(String query) {
        if (query != null && !query.trim().isEmpty()) {
            String[] words = query.trim().split("\\s+");
            if (words.length >= 3 && words[0].toUpperCase().equals("SELECT")) {
                for (int i = 1; i < words.length; i++) {
                    if (words[i].toUpperCase().equals("FROM") && i + 1 < words.length) {
                        return words[i + 1].replaceAll("[^a-zA-Z0-9_]", "");
                    }
                }
            }
        }
        return "query";
    }

    /**
     * Truncate query for display purposes.
     */
    private static String truncateQuery(String query) {
        if (query == null) return "";
        String cleaned = query.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 100 ? cleaned.substring(0, 97) + "..." : cleaned;
    }

    /**
     * Create Excel file from query result data.
     */
    private static void createExcelFile(List<LinkedHashMap<String, String>> data, String filePath, String sheetName) throws Exception {
        try {
            // Create workbook and sheet
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(sheetName);

            if (data.isEmpty()) {
                // Create empty sheet with message
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(0);
                row.createCell(0).setCellValue("No data returned by query");
            } else {
                // Create header row from first data row keys
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
                List<String> columnNames = new ArrayList<>(data.get(0).keySet());

                // Create header style
                org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

                for (int i = 0; i < columnNames.size(); i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columnNames.get(i));
                    cell.setCellStyle(headerStyle);
                }

                // Create data rows
                for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx + 1);
                    LinkedHashMap<String, String> rowData = data.get(rowIdx);

                    for (int colIdx = 0; colIdx < columnNames.size(); colIdx++) {
                        String value = rowData.get(columnNames.get(colIdx));
                        row.createCell(colIdx).setCellValue(value != null ? value : "");
                    }
                }

                // Auto-size columns
                for (int i = 0; i < columnNames.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            // Ensure output directory exists
            java.io.File file = new java.io.File(filePath);
            java.io.File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Write to file
            try (java.io.FileOutputStream fileOut = new java.io.FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }

            workbook.close();

        } catch (Exception e) {
            log.error("Failed to create Excel file: {}", e.getMessage(), e);
            throw new Exception("Excel file creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate connection parameters.
     */
    private static void validateConnectionParameters(String jdbcUrl, String username, String password) throws Exception {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new Exception("JDBC URL is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new Exception("Username is required");
        }
        if (password == null) {
            throw new Exception("Password is required");
        }

        // Basic JDBC URL format validation for PostgreSQL
        if (!jdbcUrl.toLowerCase().startsWith("jdbc:postgresql://")) {
            throw new Exception("Invalid PostgreSQL JDBC URL format. Expected: jdbc:postgresql://host:port/database");
        }
    }

    /**
     * Validate query safety by checking for dangerous SQL keywords.
     */
    private static void validateQuerySafety(String query) throws Exception {
        if (query == null) return;

        String upperQuery = query.trim().toUpperCase();

        // Check for dangerous keywords at the start of the query
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upperQuery.startsWith(keyword + " ")) {
                throw new Exception("Unsafe query operation not allowed: " + keyword);
            }
            // Also check for statements after semicolons
            if (upperQuery.contains(";" + keyword + " ")) {
                throw new Exception("Unsafe query operation not allowed: " + keyword);
            }
        }

        // Must start with SELECT for safety
        if (!upperQuery.trim().startsWith("SELECT")) {
            throw new Exception("Only SELECT queries are allowed");
        }
    }

    /**
     * Add LIMIT clause to query for preview functionality.
     */
    private static String addLimitToQuery(String query, int limit) {
        String trimmedQuery = query.trim();

        // Remove trailing semicolon if present
        if (trimmedQuery.endsWith(";")) {
            trimmedQuery = trimmedQuery.substring(0, trimmedQuery.length() - 1);
        }

        // Check if query already has LIMIT clause
        if (trimmedQuery.toUpperCase().contains(" LIMIT ")) {
            return trimmedQuery; // Return as-is if LIMIT already present
        }

        // Add LIMIT clause for PostgreSQL
        return trimmedQuery + " LIMIT " + limit;
    }

    /**
     * Get total row count for a query (without LIMIT).
     */
    private static int getTotalRowCount(Connection conn, String query) {
        try {
            // Create COUNT query by wrapping original query
            String countQuery = "SELECT COUNT(*) as total_count FROM (" + query + ") as count_subquery";

            Object countResult = getSingleResult(conn, countQuery);

            if (countResult != null) {
                return Integer.parseInt(countResult.toString());
            }

        } catch (Exception e) {
            log.debug("Could not get total row count: {}", e.getMessage());
        }

        return 0; // Return 0 if count failed
    }

    /**
     * Execute query and return results as list of maps WITHOUT closing the connection.
     * The caller is responsible for connection management.
     *
     * @param con Active database connection
     * @param query SQL query to execute
     * @return List of result rows as LinkedHashMap
     */
    private static List<LinkedHashMap<String, String>> getResultsetAsListOfMap(Connection con, String query) {
        List<LinkedHashMap<String, String>> resultList = new ArrayList<>();

        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData md = rs.getMetaData();

            while (rs.next()) {
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String key = md.getColumnName(i);
                    Object value = rs.getObject(i);
                    if (value != null) {
                        row.put(key, value.toString());
                    } else {
                        row.put(key, null);
                    }
                }
                resultList.add(row);
            }

            log.debug("Total fetched records: {}", resultList.size());

        } catch (Exception e) {
            log.error("Exception while fetching result set as list of map: {}", e.getMessage(), e);
        }
        // NOTE: Connection is NOT closed here - caller manages it

        return resultList;
    }

    /**
     * Execute a simple query and return single result (like COUNT).
     *
     * @param con Active database connection
     * @param query SQL query to execute
     * @return First column value from first row, or null if no results
     */
    private static Object getSingleResult(Connection con, String query) {
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            if (rs.next()) {
                return rs.getObject(1);
            }

        } catch (Exception e) {
            log.error("Exception while getting single result: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Get active connection count for monitoring.
     */
    public static int getActiveConnectionCount() {
        return DatabaseConnectionManager.getActiveConnectionCount();
    }

    /**
     * Execute cross-database comparison between two different database sources.
     * Connects to source database, executes source query, stores results, closes source connection.
     * Connects to target database, executes target query, stores results, closes target connection.
     * Compares the two result sets and generates 4-sheet Excel report.
     *
     * @param sourceJdbcUrl Source database JDBC URL
     * @param sourceUsername Source database username
     * @param sourcePassword Source database password
     * @param sourceQuery Source SQL query (SELECT only)
     * @param targetJdbcUrl Target database JDBC URL
     * @param targetUsername Target database username
     * @param targetPassword Target database password
     * @param targetQuery Target SQL query (SELECT only)
     * @param uniqueKey Column name(s) for unique key (comma-separated for multi-column)
     * @param ignoreColumns Set of column names to ignore during comparison
     * @param thresholds Numeric thresholds for comparison by column name
     * @param outputDir Directory where comparison report should be saved
     * @return Status map with comparison results and report location
     * @throws Exception If cross-database comparison operation fails
     */
    public static Map<String, Object> executeCrossDatabaseComparison(
            String sourceJdbcUrl, String sourceUsername, String sourcePassword, String sourceQuery,
            String targetJdbcUrl, String targetUsername, String targetPassword, String targetQuery,
            String uniqueKey, Set<String> ignoreColumns, Map<String, Double> thresholds, String outputDir) throws Exception {

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        String sourceConnectionId = null;
        String targetConnectionId = null;

        try {
            log.info("Executing cross-database comparison between different database sources");

            // Validate parameters
            validateCrossDatabaseParameters(sourceJdbcUrl, sourceUsername, sourcePassword, sourceQuery,
                    targetJdbcUrl, targetUsername, targetPassword, targetQuery, uniqueKey, outputDir);

            // Validate query safety
            validateQuerySafety(sourceQuery);
            validateQuerySafety(targetQuery);

            // Step 1: Connect to source database and execute query
            log.info("Connecting to source database: {}", sourceJdbcUrl);
            ConnectionResult sourceConnResult = connectToDatabase(sourceJdbcUrl, sourceUsername, sourcePassword);
            if (!"CONNECTED".equals(sourceConnResult.getStatus())) {
                throw new Exception("Failed to connect to source database: " + sourceConnResult.getMessage());
            }
            sourceConnectionId = sourceConnResult.getConnectionId();

            log.info("Executing source query");
            Connection sourceConn = DatabaseConnectionManager.getConnection(sourceConnectionId);
            List<LinkedHashMap<String, String>> sourceData = getResultsetAsListOfMap(sourceConn, sourceQuery);
            log.info("Source query returned {} rows", sourceData.size());

            // Close source connection
            closeConnection(sourceConnectionId);
            sourceConnectionId = null;

            // Step 2: Connect to target database and execute query
            log.info("Connecting to target database: {}", targetJdbcUrl);
            ConnectionResult targetConnResult = connectToDatabase(targetJdbcUrl, targetUsername, targetPassword);
            if (!"CONNECTED".equals(targetConnResult.getStatus())) {
                throw new Exception("Failed to connect to target database: " + targetConnResult.getMessage());
            }
            targetConnectionId = targetConnResult.getConnectionId();

            log.info("Executing target query");
            Connection targetConn = DatabaseConnectionManager.getConnection(targetConnectionId);
            List<LinkedHashMap<String, String>> targetData = getResultsetAsListOfMap(targetConn, targetQuery);
            log.info("Target query returned {} rows", targetData.size());

            // Close target connection
            closeConnection(targetConnectionId);
            targetConnectionId = null;

            // Step 3: Prepare data for comparison
            List<Map<String, String>> preparedSourceData = prepareDataForComparison(sourceData, uniqueKey, ignoreColumns);
            List<Map<String, String>> preparedTargetData = prepareDataForComparison(targetData, uniqueKey, ignoreColumns);

            // Step 4: Perform comparison using AfTableComparisonCTRLV2
            AfTableComparisonCTRLV2 comparator = new AfTableComparisonCTRLV2();
            CompareResult compareResult;

            if (thresholds != null && !thresholds.isEmpty()) {
                compareResult = comparator.getMatchResultWithThreshold(preparedSourceData, preparedTargetData, "UNI_KEY", thresholds);
            } else {
                compareResult = comparator.getMatchResult(preparedSourceData, preparedTargetData, "UNI_KEY");
            }

            // Set metadata for the report
            compareResult.setSourceType("Cross-Database Source");
            compareResult.setTargetType("Cross-Database Target");
            compareResult.setSourceLocation("Source: " + truncateQuery(sourceQuery) + " [" + extractDbName(sourceJdbcUrl) + "]");
            compareResult.setTargetLocation("Target: " + truncateQuery(targetQuery) + " [" + extractDbName(targetJdbcUrl) + "]");

            // Step 5: Generate auto filename for cross-database comparison report
            String autoFilename = generateCrossDatabaseReportFilename(sourceJdbcUrl, targetJdbcUrl, sourceQuery, targetQuery);
            String fullPath = java.nio.file.Paths.get(outputDir, autoFilename).toString();

            // Step 6: Generate 4-sheet Excel comparison report
            ExcelReportGenerator reportGenerator = new ExcelReportGenerator();
            reportGenerator.generateComparisonReport(compareResult, fullPath);

            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;

            // Build result
            result.put("status", "SUCCESS");
            result.put("reportFile", fullPath);
            result.put("autoGeneratedFilename", autoFilename);
            result.put("sourceRowCount", sourceData.size());
            result.put("targetRowCount", targetData.size());
            result.put("matchCount", compareResult.getMatchingRecords());
            result.put("mismatchCount", compareResult.getMismatchRecordCount());
            result.put("sourceExtraCount", compareResult.getSourceExtraRecords() != null ? compareResult.getSourceExtraRecords().size() : 0);
            result.put("targetExtraCount", compareResult.getTargetExtraRecords() != null ? compareResult.getTargetExtraRecords().size() : 0);
            result.put("executionTime", executionTime + "ms");
            result.put("sourceDatabase", extractDbName(sourceJdbcUrl));
            result.put("targetDatabase", extractDbName(targetJdbcUrl));
            result.put("message", "Cross-database comparison completed successfully");

            log.info("Cross-database comparison completed. Source: {} rows, Target: {} rows, Matches: {}, Mismatches: {}, Time: {}ms",
                    sourceData.size(), targetData.size(), compareResult.getMatchingRecords(), compareResult.getMismatchRecordCount(), executionTime);

            return result;

        } catch (Exception e) {
            log.error("Cross-database comparison failed: {}", e.getMessage(), e);

            // Ensure connections are closed in case of error
            if (sourceConnectionId != null) {
                try {
                    closeConnection(sourceConnectionId);
                } catch (Exception closeEx) {
                    log.warn("Failed to close source connection during error cleanup: {}", closeEx.getMessage());
                }
            }
            if (targetConnectionId != null) {
                try {
                    closeConnection(targetConnectionId);
                } catch (Exception closeEx) {
                    log.warn("Failed to close target connection during error cleanup: {}", closeEx.getMessage());
                }
            }

            result.put("status", "FAILED");
            result.put("reportFile", "");
            result.put("autoGeneratedFilename", "");
            result.put("sourceRowCount", 0);
            result.put("targetRowCount", 0);
            result.put("matchCount", 0);
            result.put("mismatchCount", 0);
            result.put("sourceExtraCount", 0);
            result.put("targetExtraCount", 0);
            result.put("executionTime", (System.currentTimeMillis() - startTime) + "ms");
            result.put("sourceDatabase", extractDbName(sourceJdbcUrl));
            result.put("targetDatabase", extractDbName(targetJdbcUrl));
            result.put("message", "Cross-database comparison failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Validate cross-database comparison parameters.
     */
    private static void validateCrossDatabaseParameters(String sourceJdbcUrl, String sourceUsername, String sourcePassword, String sourceQuery,
                                                       String targetJdbcUrl, String targetUsername, String targetPassword, String targetQuery,
                                                       String uniqueKey, String outputDir) throws Exception {
        // Source database parameters
        if (sourceJdbcUrl == null || sourceJdbcUrl.trim().isEmpty()) {
            throw new Exception("Source JDBC URL is required");
        }
        if (sourceUsername == null || sourceUsername.trim().isEmpty()) {
            throw new Exception("Source username is required");
        }
        if (sourcePassword == null) {
            throw new Exception("Source password is required");
        }
        if (sourceQuery == null || sourceQuery.trim().isEmpty()) {
            throw new Exception("Source query is required");
        }

        // Target database parameters
        if (targetJdbcUrl == null || targetJdbcUrl.trim().isEmpty()) {
            throw new Exception("Target JDBC URL is required");
        }
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new Exception("Target username is required");
        }
        if (targetPassword == null) {
            throw new Exception("Target password is required");
        }
        if (targetQuery == null || targetQuery.trim().isEmpty()) {
            throw new Exception("Target query is required");
        }

        // Common parameters
        if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
            throw new Exception("Unique key is required");
        }
        if (outputDir == null || outputDir.trim().isEmpty()) {
            throw new Exception("Output directory is required");
        }

        // Validate JDBC URL formats
        if (!sourceJdbcUrl.toLowerCase().startsWith("jdbc:postgresql://")) {
            throw new Exception("Invalid source PostgreSQL JDBC URL format. Expected: jdbc:postgresql://host:port/database");
        }
        if (!targetJdbcUrl.toLowerCase().startsWith("jdbc:postgresql://")) {
            throw new Exception("Invalid target PostgreSQL JDBC URL format. Expected: jdbc:postgresql://host:port/database");
        }
    }

    /**
     * Extract database name from JDBC URL for reporting.
     */
    private static String extractDbName(String jdbcUrl) {
        if (jdbcUrl == null) return "unknown";
        try {
            // Extract database name from jdbc:postgresql://host:port/dbname
            String[] parts = jdbcUrl.split("/");
            if (parts.length > 0) {
                String dbName = parts[parts.length - 1];
                // Remove any query parameters
                if (dbName.contains("?")) {
                    dbName = dbName.substring(0, dbName.indexOf("?"));
                }
                return dbName;
            }
        } catch (Exception e) {
            log.debug("Could not extract database name from JDBC URL: {}", jdbcUrl);
        }
        return "unknown";
    }

    /**
     * Generate auto filename for cross-database comparison reports.
     */
    private static String generateCrossDatabaseReportFilename(String sourceJdbcUrl, String targetJdbcUrl, String sourceQuery, String targetQuery) {
        String sourceDb = extractDbName(sourceJdbcUrl);
        String targetDb = extractDbName(targetJdbcUrl);
        String sourceTable = extractTableHint(sourceQuery);
        String targetTable = extractTableHint(targetQuery);

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        return String.format("cross_db_%s_%s_vs_%s_%s_comparison_%s.xlsx",
                sourceDb, sourceTable, targetDb, targetTable, timestamp);
    }

    /**
     * Close all active connections (cleanup utility).
     */
    public static void closeAllConnections() {
        DatabaseConnectionManager.closeAllConnections();
    }
}