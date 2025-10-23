package com.cacib.auf.mcp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cacib.auf.core.comparison.model.CompareResult;
import com.cacib.auf.core.comparison.table.AfTableComparisonCTRLV2;
import com.cacib.auf.core.comparison.excel.ExcelReportGenerator;

/**
 * Simplified MCP wrapper for CSV comparison functionality.
 * Uses AfCsvReader + AfTableComparisonCTRLV2 for all comparisons.
 * Always generates 4-sheet Excel reports with comprehensive comparison data.
 */
public class CsvComparisonMCPWrapper {

    private static final Logger log = LoggerFactory.getLogger(CsvComparisonMCPWrapper.class);

    // Excel report generator for 4-sheet reports
    private static final ExcelReportGenerator reportGenerator = new ExcelReportGenerator();

    // Default values
    private static final String DEFAULT_DELIMITER = ",";
    private static final String DEFAULT_UNIQUE_KEY = "ID";
    private static final boolean DEFAULT_SKIP_HEADER = false;

    /**
     * Unified CSV comparison method with mandatory 4-sheet Excel report generation.
     * Uses AfCsvReader + AfTableComparisonCTRLV2 for all comparisons.
     *
     * @param file1Path Path to first CSV file
     * @param file2Path Path to second CSV file
     * @param delimiter1 Delimiter for first CSV file
     * @param delimiter2 Delimiter for second CSV file
     * @param skipHeader1 Whether to skip header in first file
     * @param skipHeader2 Whether to skip header in second file
     * @param uniqueKey Name of unique key column (comma-separated for multi-column keys)
     * @param thresholds Map of column names to percentage thresholds for numeric comparisons
     * @param ignoreColumns CSV string of columns to ignore during comparison
     * @param outputPath Custom output path for Excel report
     * @return CompareResult with mismatch details and Excel report generated
     * @throws FileNotFoundException If CSV files are not found
     * @throws IOException If there's an error reading the files or generating report
     */
    public static CompareResult compareCsvFiles(
            String file1Path,
            String file2Path,
            String delimiter1,
            String delimiter2,
            Boolean skipHeader1,
            Boolean skipHeader2,
            String uniqueKey,
            Map<String, Double> thresholds,
            String ignoreColumns,
            String outputPath
    ) throws FileNotFoundException, IOException {

        log.info("Starting unified CSV comparison: {} vs {}", file1Path, file2Path);

        // Validate input files
        validateCsvFiles(file1Path, file2Path);

        // Auto-detect delimiters if not provided by user
        String finalDelimiter1 = delimiter1;
        if (finalDelimiter1 == null || finalDelimiter1.trim().isEmpty()) {
            finalDelimiter1 = CsvDelimiterDetector.detectDelimiter(file1Path);
            log.info("Auto-detected delimiter '{}' for file1: {}",
                    CsvDelimiterDetector.formatDelimiterForLog(finalDelimiter1), file1Path);
        }

        String finalDelimiter2 = delimiter2;
        if (finalDelimiter2 == null || finalDelimiter2.trim().isEmpty()) {
            finalDelimiter2 = CsvDelimiterDetector.detectDelimiter(file2Path);
            log.info("Auto-detected delimiter '{}' for file2: {}",
                    CsvDelimiterDetector.formatDelimiterForLog(finalDelimiter2), file2Path);
        }

        // Set default values for optional parameters
        Map<String, Object> defaults = setDefaults(finalDelimiter1, finalDelimiter2, skipHeader1, skipHeader2, uniqueKey, thresholds, ignoreColumns);

        String processedDelimiter1 = (String) defaults.get("delimiter1");
        String processedDelimiter2 = (String) defaults.get("delimiter2");
        boolean finalSkipHeader1 = (Boolean) defaults.get("skipHeader1");
        boolean finalSkipHeader2 = (Boolean) defaults.get("skipHeader2");
        String finalUniqueKey = (String) defaults.get("uniqueKey");
        @SuppressWarnings("unchecked")
        Map<String, Double> finalThresholds = (Map<String, Double>) defaults.get("thresholds");
        String finalIgnoreColumns = (String) defaults.get("ignoreColumns");

        log.info("Using unified architecture: delimiters='{}','{}'  skipHeaders={},{} uniqueKey='{}' ignoreColumns='{}'",
                processedDelimiter1, processedDelimiter2, finalSkipHeader1, finalSkipHeader2, finalUniqueKey, finalIgnoreColumns);

        try {
            // Convert CSV files to table format
            List<Map<String, String>> sourceTable = CsvToTableConverter.convertCsvToTable(
                    file1Path, processedDelimiter1, finalSkipHeader1, finalUniqueKey);

            List<Map<String, String>> targetTable = CsvToTableConverter.convertCsvToTable(
                    file2Path, processedDelimiter2, finalSkipHeader2, finalUniqueKey);

            log.info("Converted CSV files to tables. Source: {} rows, Target: {} rows",
                    sourceTable.size(), targetTable.size());

            // Perform table comparison using AfTableComparisonCTRLV2 (unified architecture)
            AfTableComparisonCTRLV2 comparisonController = new AfTableComparisonCTRLV2();
            CompareResult result;

            // Convert ignore columns string to Set if provided
            Set<String> ignoreColumnsSet = null;
            if (finalIgnoreColumns != null && !finalIgnoreColumns.trim().isEmpty()) {
                ignoreColumnsSet = new HashSet<>();
                String[] columns = finalIgnoreColumns.split(",");
                for (String column : columns) {
                    String trimmed = column.trim();
                    if (!trimmed.isEmpty()) {
                        ignoreColumnsSet.add(trimmed);
                    }
                }
            }

            // Get effective key column (handles composite keys)
            String effectiveKeyColumn = CsvToTableConverter.getEffectiveKeyColumn(finalUniqueKey);

            // Use the unified method that handles all cases
            log.info("CSV comparison using unified method with effective key column: {}", effectiveKeyColumn);
            result = comparisonController.getMatchResultWithIgnoredColumns(
                    sourceTable, targetTable, effectiveKeyColumn, finalThresholds, ignoreColumnsSet);

            // Set source record count and metadata
            result.setSourceRecordCount(sourceTable.size());
            result.setSourceType("CSV");
            result.setTargetType("CSV");
            result.setSourceLocation(file1Path);
            result.setTargetLocation(file2Path);

            // Always generate 4-sheet Excel report
            String reportPath = determineReportPath(outputPath, file1Path, file2Path);
            reportGenerator.generateComparisonReport(result, reportPath);
            log.info("4-sheet Excel report generated: {}", reportPath);

            log.info("CSV comparison completed. Source records: {}, Mismatched records: {}",
                    sourceTable.size(), result.getMismatchRecordCount());

            return result;

        } catch (Exception e) {
            log.error("CSV comparison failed", e);
            throw new IOException("CSV comparison failed: " + e.getMessage(), e);
        }
    }

    /**
     * Simplified CSV comparison with minimal parameters.
     * Uses default settings and generates 4-sheet Excel report.
     */
    public static CompareResult compareCsvFilesSimple(String file1Path, String file2Path, String outputPath)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, null, null, null, null, null, null, null, outputPath);
    }

    /**
     * CSV comparison with custom unique key.
     */
    public static CompareResult compareCsvFilesWithUniqueKey(
            String file1Path, String file2Path, String uniqueKey, String outputPath)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, null, null, null, null, uniqueKey, null, null, outputPath);
    }

    /**
     * CSV comparison with custom delimiters.
     */
    public static CompareResult compareCsvFilesWithDelimiters(
            String file1Path, String file2Path, String delimiter1, String delimiter2, String outputPath)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, delimiter1, delimiter2, null, null, null, null, null, outputPath);
    }

    /**
     * CSV comparison with thresholds for numeric columns.
     */
    public static CompareResult compareCsvFilesWithThresholds(
            String file1Path, String file2Path, String uniqueKey, Map<String, Double> thresholds, String outputPath)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, null, null, null, null, uniqueKey, thresholds, null, outputPath);
    }

    /**
     * CSV comparison with ignore columns.
     */
    public static CompareResult compareCsvFilesWithIgnoreColumns(
            String file1Path, String file2Path, String uniqueKey, String ignoreColumns, String outputPath)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, null, null, null, null, uniqueKey, null, ignoreColumns, outputPath);
    }

    /**
     * Validate that CSV files exist and are readable.
     */
    private static void validateCsvFiles(String file1Path, String file2Path) throws FileNotFoundException {
        File file1 = new File(file1Path);
        File file2 = new File(file2Path);

        if (!file1.exists()) {
            throw new FileNotFoundException("First CSV file not found: " + file1Path);
        }
        if (!file2.exists()) {
            throw new FileNotFoundException("Second CSV file not found: " + file2Path);
        }
        if (!file1.canRead()) {
            throw new FileNotFoundException("Cannot read first CSV file: " + file1Path);
        }
        if (!file2.canRead()) {
            throw new FileNotFoundException("Cannot read second CSV file: " + file2Path);
        }

        log.debug("CSV files validated successfully");
    }

    /**
     * Set default values for optional parameters.
     */
    private static Map<String, Object> setDefaults(
            String delimiter1,
            String delimiter2,
            Boolean skipHeader1,
            Boolean skipHeader2,
            String uniqueKey,
            Map<String, Double> thresholds,
            String ignoreColumns
    ) {
        Map<String, Object> defaults = new HashMap<>();

        // Set delimiter defaults
        defaults.put("delimiter1", (delimiter1 != null && !delimiter1.trim().isEmpty()) ? delimiter1 : DEFAULT_DELIMITER);
        defaults.put("delimiter2", (delimiter2 != null && !delimiter2.trim().isEmpty()) ? delimiter2 : DEFAULT_DELIMITER);

        // Set skip header defaults
        defaults.put("skipHeader1", skipHeader1 != null ? skipHeader1 : DEFAULT_SKIP_HEADER);
        defaults.put("skipHeader2", skipHeader2 != null ? skipHeader2 : DEFAULT_SKIP_HEADER);

        // Set unique key column default
        defaults.put("uniqueKey", (uniqueKey != null && !uniqueKey.trim().isEmpty()) ? uniqueKey : DEFAULT_UNIQUE_KEY);

        // Set thresholds (can be null)
        defaults.put("thresholds", thresholds);

        // Set ignore columns (can be null)
        defaults.put("ignoreColumns", ignoreColumns);

        return defaults;
    }

    /**
     * Determine the appropriate path for the Excel report output.
     */
    private static String determineReportPath(String outputPath, String file1Path, String file2Path) {
        if (outputPath != null && !outputPath.trim().isEmpty()) {
            // Ensure .xlsx extension
            if (!outputPath.toLowerCase().endsWith(".xlsx")) {
                outputPath += ".xlsx";
            }
            return outputPath;
        } else {
            // Generate default path based on input file names
            String baseName1 = new File(file1Path).getName();
            String baseName2 = new File(file2Path).getName();

            // Remove extensions
            if (baseName1.contains(".")) {
                baseName1 = baseName1.substring(0, baseName1.lastIndexOf('.'));
            }
            if (baseName2.contains(".")) {
                baseName2 = baseName2.substring(0, baseName2.lastIndexOf('.'));
            }

            return baseName1 + "_vs_" + baseName2 + "_csv_comparison_report.xlsx";
        }
    }
}