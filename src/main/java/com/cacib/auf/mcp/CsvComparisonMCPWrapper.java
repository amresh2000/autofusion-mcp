package com.cacib.auf.mcp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cacib.auf.core.comparison.model.CompareResult;
import com.cacib.auf.core.comparison.table.ATableComparisonCTRL;
import com.cacib.auf.core.comparison.table.ATableComparisonCTRLV2;

/**
 * MCP wrapper for CSV comparison functionality.
 * Converts CSV files to table format and performs comparison using existing table comparison engine.
 */
public class CsvComparisonMCPWrapper {

    private static final Logger log = LoggerFactory.getLogger(CsvComparisonMCPWrapper.class);

    // Default values
    private static final String DEFAULT_DELIMITER = ",";
    private static final String DEFAULT_UNIQUE_KEY = "UNL_KEY";
    private static final boolean DEFAULT_SKIP_HEADER = false;

    /**
     * Main comparison method for MCP tool.
     *
     * @param file1Path Path to first CSV file
     * @param file2Path Path to second CSV file
     * @param delimiter1 Delimiter for first CSV file
     * @param delimiter2 Delimiter for second CSV file
     * @param skipHeader1 Whether to skip header in first file
     * @param skipHeader2 Whether to skip header in second file
     * @param uniqueKeyColumn Name of unique key column
     * @param thresholds Map of column names to percentage thresholds for numeric comparisons
     * @return CompareResult with mismatch details
     * @throws FileNotFoundException If CSV files are not found
     * @throws IOException If there's an error reading the files
     */
    public static CompareResult compareCsvFiles(
            String file1Path,
            String file2Path,
            String delimiter1,
            String delimiter2,
            Boolean skipHeader1,
            Boolean skipHeader2,
            String uniqueKeyColumn,
            Map<String, Double> thresholds
    ) throws FileNotFoundException, IOException {

        log.info("Starting CSV comparison: {} vs {}", file1Path, file2Path);

        // Validate input files
        validateCsvFiles(file1Path, file2Path);

        // Set default values for optional parameters
        Map<String, Object> defaults = setDefaults(delimiter1, delimiter2, skipHeader1, skipHeader2, uniqueKeyColumn, thresholds);

        String finalDelimiter1 = (String) defaults.get("delimiter1");
        String finalDelimiter2 = (String) defaults.get("delimiter2");
        boolean finalSkipHeader1 = (Boolean) defaults.get("skipHeader1");
        boolean finalSkipHeader2 = (Boolean) defaults.get("skipHeader2");
        String finalUniqueKeyColumn = (String) defaults.get("uniqueKeyColumn");
        @SuppressWarnings("unchecked")
        Map<String, Double> finalThresholds = (Map<String, Double>) defaults.get("thresholds");

        log.info("Using delimiters: '{}' and '{}', skipHeaders: {} and {}, uniqueKey: '{}'",
                finalDelimiter1, finalDelimiter2, finalSkipHeader1, finalSkipHeader2, finalUniqueKeyColumn);

        try {
            // Convert CSV files to table format
            List<Map<String, String>> sourceTable = CsvToTableConverter.convertCsvToTable(
                    file1Path, finalDelimiter1, finalSkipHeader1, finalUniqueKeyColumn);

            List<Map<String, String>> targetTable = CsvToTableConverter.convertCsvToTable(
                    file2Path, finalDelimiter2, finalSkipHeader2, finalUniqueKeyColumn);

            log.info("Converted CSV files to tables. Source: {} rows, Target: {} rows",
                    sourceTable.size(), targetTable.size());

            // Perform table comparison
            ATableComparisonCTRL comparisonController = new ATableComparisonCTRL();
            CompareResult result;

            if (finalThresholds != null && !finalThresholds.isEmpty()) {
                log.info("Using thresholds for comparison: {}", finalThresholds);
                result = comparisonController.getMatchResultWithThresholds(sourceTable, targetTable, finalThresholds);
            } else {
                result = comparisonController.getMatchResultHelper(sourceTable, targetTable);
            }

            // Set source record count
            result.setSourceRecordCount(sourceTable.size());

            log.info("CSV comparison completed. Source records: {}, Mismatched records: {}",
                    sourceTable.size(), result.getMismatchRecordCount());

            return result;

        } catch (Exception e) {
            log.error("CSV comparison failed", e);
            throw new IOException("CSV comparison failed: " + e.getMessage(), e);
        }
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
            String uniqueKeyColumn,
            Map<String, Double> thresholds
    ) {
        Map<String, Object> defaults = new HashMap<>();

        // Set delimiter defaults
        defaults.put("delimiter1", (delimiter1 != null && !delimiter1.trim().isEmpty()) ? delimiter1 : DEFAULT_DELIMITER);
        defaults.put("delimiter2", (delimiter2 != null && !delimiter2.trim().isEmpty()) ? delimiter2 : DEFAULT_DELIMITER);

        // Set skip header defaults
        defaults.put("skipHeader1", skipHeader1 != null ? skipHeader1 : DEFAULT_SKIP_HEADER);
        defaults.put("skipHeader2", skipHeader2 != null ? skipHeader2 : DEFAULT_SKIP_HEADER);

        // Set unique key column default
        defaults.put("uniqueKeyColumn", (uniqueKeyColumn != null && !uniqueKeyColumn.trim().isEmpty()) ? uniqueKeyColumn : DEFAULT_UNIQUE_KEY);

        // Set thresholds (can be null)
        defaults.put("thresholds", thresholds);

        return defaults;
    }

    /**
     * Create a quick CSV comparison with minimal parameters.
     * Useful for simple comparisons with default settings.
     */
    public static CompareResult compareCsvFilesSimple(String file1Path, String file2Path)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, null, null, null, null, null, null);
    }

    /**
     * Create a CSV comparison with custom delimiters but default other settings.
     */
    public static CompareResult compareCsvFilesWithDelimiters(
            String file1Path, String file2Path, String delimiter1, String delimiter2)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, delimiter1, delimiter2, null, null, null, null);
    }

    /**
     * Create a CSV comparison with headers skipped.
     */
    public static CompareResult compareCsvFilesWithHeaders(
            String file1Path, String file2Path, boolean skipHeader1, boolean skipHeader2)
            throws FileNotFoundException, IOException {
        return compareCsvFiles(file1Path, file2Path, null, null, skipHeader1, skipHeader2, null, null);
    }

    /**
     * Enhanced CSV comparison method using ATableComparisonCTRLV2 with advanced features.
     *
     * @param file1Path Path to first CSV file
     * @param file2Path Path to second CSV file
     * @param delimiter1 Delimiter for first CSV file
     * @param delimiter2 Delimiter for second CSV file
     * @param skipHeader1 Whether to skip header in first file
     * @param skipHeader2 Whether to skip header in second file
     * @param uniqueKeyColumn Name of unique key column (legacy parameter)
     * @param thresholds Map of column names to percentage thresholds for numeric comparisons
     * @param customUniqueKey Custom unique key column name (overrides uniqueKeyColumn)
     * @param ignoreColumns CSV string of columns to ignore during comparison
     * @return CompareResult with mismatch details
     * @throws FileNotFoundException If CSV files are not found
     * @throws IOException If there's an error reading the files
     */
    public static CompareResult compareCsvFilesV2(
            String file1Path,
            String file2Path,
            String delimiter1,
            String delimiter2,
            Boolean skipHeader1,
            Boolean skipHeader2,
            String uniqueKeyColumn,
            Map<String, Double> thresholds,
            String customUniqueKey,
            String ignoreColumns
    ) throws FileNotFoundException, IOException {

        log.info("Starting CSV comparison V2: {} vs {}", file1Path, file2Path);

        // Validate input files
        validateCsvFiles(file1Path, file2Path);

        // Set default values for optional parameters
        Map<String, Object> defaults = setDefaultsV2(delimiter1, delimiter2, skipHeader1, skipHeader2,
                                                    uniqueKeyColumn, thresholds, customUniqueKey, ignoreColumns);

        String finalDelimiter1 = (String) defaults.get("delimiter1");
        String finalDelimiter2 = (String) defaults.get("delimiter2");
        boolean finalSkipHeader1 = (Boolean) defaults.get("skipHeader1");
        boolean finalSkipHeader2 = (Boolean) defaults.get("skipHeader2");
        String finalUniqueKeyColumn = (String) defaults.get("uniqueKeyColumn");
        @SuppressWarnings("unchecked")
        Map<String, Double> finalThresholds = (Map<String, Double>) defaults.get("thresholds");
        String finalIgnoreColumns = (String) defaults.get("ignoreColumns");

        log.info("Using delimiters: '{}' and '{}', skipHeaders: {} and {}, uniqueKey: '{}', ignoreColumns: '{}'",
                finalDelimiter1, finalDelimiter2, finalSkipHeader1, finalSkipHeader2, finalUniqueKeyColumn, finalIgnoreColumns);

        try {
            // Convert CSV files to table format
            List<Map<String, String>> sourceTable = CsvToTableConverter.convertCsvToTable(
                    file1Path, finalDelimiter1, finalSkipHeader1, finalUniqueKeyColumn);

            List<Map<String, String>> targetTable = CsvToTableConverter.convertCsvToTable(
                    file2Path, finalDelimiter2, finalSkipHeader2, finalUniqueKeyColumn);

            log.info("Converted CSV files to tables. Source: {} rows, Target: {} rows",
                    sourceTable.size(), targetTable.size());

            // Perform table comparison using V2 controller
            ATableComparisonCTRLV2 comparisonController = new ATableComparisonCTRLV2();
            CompareResult result;

            if (finalIgnoreColumns != null && !finalIgnoreColumns.trim().isEmpty()) {
                // Use V2 method with ignore columns
                if (finalThresholds != null && !finalThresholds.isEmpty()) {
                    log.info("Using V2 comparison with thresholds: {} and ignore columns: {}", finalThresholds, finalIgnoreColumns);
                    result = comparisonController.getMatchResultWithIgnoredColumns(
                            sourceTable, targetTable, finalUniqueKeyColumn, finalThresholds, finalIgnoreColumns);
                } else {
                    log.info("Using V2 comparison with ignore columns: {}", finalIgnoreColumns);
                    result = comparisonController.getMatchResultWithIgnoredColumns(
                            sourceTable, targetTable, finalUniqueKeyColumn, null, finalIgnoreColumns);
                }
            } else if (finalThresholds != null && !finalThresholds.isEmpty()) {
                // Use V2 method with thresholds only
                log.info("Using V2 comparison with thresholds: {}", finalThresholds);
                result = comparisonController.getMatchResultWithThreshold(sourceTable, targetTable, finalUniqueKeyColumn, finalThresholds);
            } else {
                // Use V2 method with custom unique key only
                log.info("Using V2 comparison with custom unique key: {}", finalUniqueKeyColumn);
                result = comparisonController.getMatchResultHelper(sourceTable, targetTable, finalUniqueKeyColumn);
            }

            // Set source record count
            result.setSourceRecordCount(sourceTable.size());

            log.info("CSV comparison V2 completed. Source records: {}, Mismatched records: {}",
                    sourceTable.size(), result.getMismatchRecordCount());

            return result;

        } catch (Exception e) {
            log.error("CSV comparison V2 failed", e);
            throw new IOException("CSV comparison V2 failed: " + e.getMessage(), e);
        }
    }

    /**
     * Set default values for V2 optional parameters.
     */
    private static Map<String, Object> setDefaultsV2(
            String delimiter1,
            String delimiter2,
            Boolean skipHeader1,
            Boolean skipHeader2,
            String uniqueKeyColumn,
            Map<String, Double> thresholds,
            String customUniqueKey,
            String ignoreColumns
    ) {
        Map<String, Object> defaults = new HashMap<>();

        // Set delimiter defaults
        defaults.put("delimiter1", (delimiter1 != null && !delimiter1.trim().isEmpty()) ? delimiter1 : DEFAULT_DELIMITER);
        defaults.put("delimiter2", (delimiter2 != null && !delimiter2.trim().isEmpty()) ? delimiter2 : DEFAULT_DELIMITER);

        // Set skip header defaults
        defaults.put("skipHeader1", skipHeader1 != null ? skipHeader1 : DEFAULT_SKIP_HEADER);
        defaults.put("skipHeader2", skipHeader2 != null ? skipHeader2 : DEFAULT_SKIP_HEADER);

        // Set unique key column - customUniqueKey takes precedence over uniqueKeyColumn
        String finalUniqueKey = DEFAULT_UNIQUE_KEY;
        if (customUniqueKey != null && !customUniqueKey.trim().isEmpty()) {
            finalUniqueKey = customUniqueKey.trim();
        } else if (uniqueKeyColumn != null && !uniqueKeyColumn.trim().isEmpty()) {
            finalUniqueKey = uniqueKeyColumn.trim();
        }
        defaults.put("uniqueKeyColumn", finalUniqueKey);

        // Set thresholds (can be null)
        defaults.put("thresholds", thresholds);

        // Set ignore columns (can be null)
        defaults.put("ignoreColumns", ignoreColumns);

        return defaults;
    }

    /**
     * Enhanced CSV comparison with custom unique key only.
     */
    public static CompareResult compareCsvFilesWithCustomKey(
            String file1Path, String file2Path, String customUniqueKey)
            throws FileNotFoundException, IOException {
        return compareCsvFilesV2(file1Path, file2Path, null, null, null, null, null, null, customUniqueKey, null);
    }

    /**
     * Enhanced CSV comparison with ignore columns only.
     */
    public static CompareResult compareCsvFilesWithIgnoreColumns(
            String file1Path, String file2Path, String ignoreColumns)
            throws FileNotFoundException, IOException {
        return compareCsvFilesV2(file1Path, file2Path, null, null, null, null, null, null, null, ignoreColumns);
    }

    /**
     * Enhanced CSV comparison with both custom key and ignore columns.
     */
    public static CompareResult compareCsvFilesWithCustomKeyAndIgnoreColumns(
            String file1Path, String file2Path, String customUniqueKey, String ignoreColumns)
            throws FileNotFoundException, IOException {
        return compareCsvFilesV2(file1Path, file2Path, null, null, null, null, null, null, customUniqueKey, ignoreColumns);
    }
}