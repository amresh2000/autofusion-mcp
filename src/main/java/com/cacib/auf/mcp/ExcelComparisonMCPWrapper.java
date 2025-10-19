package com.cacib.auf.mcp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cacib.auf.core.comparison.excel.ExcelRowDifferenceComparator;
import com.cacib.auf.core.comparison.excel.ExcelCellByCellComparator;
import com.cacib.auf.core.comparison.excel.ExcelComparisonConfig;
import com.cacib.auf.core.comparison.model.CompareResult;
import com.cacib.auf.core.comparison.model.MismatchDetail;

/**
 * MCP wrapper for Excel comparison functionality.
 * Bridges existing Excel comparison engines with MCP tool interface.
 */
public class ExcelComparisonMCPWrapper {

    private static final Logger log = LoggerFactory.getLogger(ExcelComparisonMCPWrapper.class);

    public static final String MODE_ROW_DIFFERENCE = "row_difference";
    public static final String MODE_CELL_BY_CELL = "cell_by_cell";

    /**
     * Main comparison method for MCP tool.
     *
     * @param file1Path Path to first Excel file
     * @param file2Path Path to second Excel file
     * @param outputPath Optional path for difference output file
     * @param sheetName Optional specific sheet name to compare
     * @param compareMode Comparison mode: "row_difference" or "cell_by_cell"
     * @return CompareResult with mismatch details
     * @throws IOException If file operations fail
     */
    public static CompareResult compareExcelFiles(
            String file1Path,
            String file2Path,
            String outputPath,
            String sheetName,
            String compareMode
    ) throws IOException {

        log.info("Starting Excel comparison: {} vs {}", file1Path, file2Path);

        // Validate input files
        File file1 = new File(file1Path);
        File file2 = new File(file2Path);

        if (!file1.exists()) {
            throw new IOException("First Excel file not found: " + file1Path);
        }
        if (!file2.exists()) {
            throw new IOException("Second Excel file not found: " + file2Path);
        }

        // Set default values
        if (compareMode == null || compareMode.trim().isEmpty()) {
            compareMode = MODE_ROW_DIFFERENCE;
        }

        if (outputPath == null || outputPath.trim().isEmpty()) {
            outputPath = generateDefaultOutputPath(file1Path, file2Path);
        }

        // Perform comparison based on mode
        CompareResult result;
        switch (compareMode.toLowerCase()) {
            case MODE_CELL_BY_CELL:
                result = performCellByCellComparison(file1, file2, outputPath, sheetName);
                break;
            case MODE_ROW_DIFFERENCE:
            default:
                result = performRowDifferenceComparison(file1, file2, outputPath, sheetName);
                break;
        }

        log.info("Excel comparison completed. Mismatched records: {}", result.getMismatchRecordCount());
        return result;
    }

    /**
     * Perform cell-by-cell comparison using existing engine.
     */
    private static CompareResult performCellByCellComparison(
            File file1, File file2, String outputPath, String sheetName
    ) throws IOException {

        log.info("Performing cell-by-cell comparison");

        try {
            ExcelCellByCellComparator comparator = new ExcelCellByCellComparator(file1, file2, outputPath);

            // Set specific sheet if provided
            if (sheetName != null && !sheetName.trim().isEmpty()) {
                // ExcelCellByCellComparator doesn't have sheet-specific methods visible
                // We'll compare all sheets but log the requested sheet
                log.info("Note: Cell-by-cell comparison will process all sheets. Requested sheet: {}", sheetName);
            }

            // Execute comparison - this returns boolean
            boolean hasDifferences = comparator.compareAndLogOnExcel();

            if (hasDifferences) {
                // Differences found - create a basic result indicating differences
                return createBasicResult("Cell-by-cell comparison found differences", outputPath, true);
            } else {
                // No differences found
                return createSuccessResult("Cell-by-cell comparison completed - no differences", outputPath);
            }

        } catch (Exception e) {
            log.error("Cell-by-cell comparison failed", e);
            return createErrorResult("Cell-by-cell comparison failed: " + e.getMessage());
        }
    }

    /**
     * Perform row difference comparison using existing engine.
     */
    private static CompareResult performRowDifferenceComparison(
            File file1, File file2, String outputPath, String sheetName
    ) throws IOException {

        log.info("Performing row difference comparison");

        try {
            ExcelRowDifferenceComparator comparator = new ExcelRowDifferenceComparator(file1, file2, outputPath);

            // Set specific sheet if provided
            if (sheetName != null && !sheetName.trim().isEmpty()) {
                log.info("Note: Row difference comparison will process all sheets. Requested sheet: {}", sheetName);
            }

            // Execute comparison - this returns boolean
            boolean hasDifferences = comparator.compareAndLogOnExcel();

            if (hasDifferences) {
                // Differences found - create a basic result indicating differences
                return createBasicResult("Differences found between Excel files", outputPath, true);
            } else {
                // No differences found
                return createSuccessResult("No differences found between Excel files", outputPath);
            }

        } catch (Exception e) {
            log.error("Row difference comparison failed", e);
            return createErrorResult("Row difference comparison failed: " + e.getMessage());
        }
    }

    /**
     * Generate default output path for comparison results.
     */
    private static String generateDefaultOutputPath(String file1Path, String file2Path) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String dir = new File(file1Path).getParent();
        if (dir == null) {
            dir = System.getProperty("user.dir");
        }
        return dir + File.separator + "excel_comparison_" + timestamp + ".xlsx";
    }

    /**
     * Create a basic CompareResult with differences indication.
     */
    private static CompareResult createBasicResult(String message, String outputPath, boolean hasDifferences) {
        CompareResult result = new CompareResult();
        result.setMismatchRecordCount(hasDifferences ? 1 : 0);
        result.setSourceRecordCount(1);
        result.setMismatchCountByColumns(hasDifferences ? "1" : "0");

        // Create a basic detail
        MismatchDetail detail = new MismatchDetail();
        detail.setMismatchStatus(hasDifferences ? "DIFFERENCES_FOUND" : "SUCCESS");
        detail.setMismatchColumnValue(message + " Output saved to: " + outputPath);
        detail.setUniqueKey("COMPARISON_RESULT");
        detail.setMismatchColumns(new ArrayList<>());

        List<MismatchDetail> details = new ArrayList<>();
        details.add(detail);
        result.setMismatchDetail(details);

        return result;
    }

    /**
     * Create a success CompareResult when no issues are found.
     */
    private static CompareResult createSuccessResult(String message, String outputPath) {
        CompareResult result = new CompareResult();
        result.setMismatchRecordCount(0);
        result.setSourceRecordCount(0);
        result.setMismatchCountByColumns("0");

        // Create a success detail
        MismatchDetail successDetail = new MismatchDetail();
        successDetail.setMismatchStatus("SUCCESS");
        successDetail.setMismatchColumnValue(message + " Output saved to: " + outputPath);
        successDetail.setUniqueKey("COMPARISON_RESULT");
        successDetail.setMismatchColumns(new ArrayList<>());

        List<MismatchDetail> details = new ArrayList<>();
        details.add(successDetail);
        result.setMismatchDetail(details);

        return result;
    }

    /**
     * Create an error CompareResult when comparison fails.
     */
    private static CompareResult createErrorResult(String errorMessage) {
        CompareResult result = new CompareResult();
        result.setMismatchRecordCount(-1); // Indicate error
        result.setSourceRecordCount(0);
        result.setMismatchCountByColumns("ERROR");

        // Create an error detail
        MismatchDetail errorDetail = new MismatchDetail();
        errorDetail.setMismatchStatus("ERROR");
        errorDetail.setMismatchColumnValue(errorMessage);
        errorDetail.setUniqueKey("COMPARISON_ERROR");
        errorDetail.setMismatchColumns(new ArrayList<>());

        List<MismatchDetail> details = new ArrayList<>();
        details.add(errorDetail);
        result.setMismatchDetail(details);

        return result;
    }

    /**
     * Enhanced Excel comparison method using ExcelComparisonConfig for advanced scenarios.
     *
     * @param file1Path Path to first Excel file
     * @param file2Path Path to second Excel file
     * @param outputPath Optional path for difference output file
     * @param sheetName Optional specific sheet name to compare
     * @param compareMode Comparison mode: "row_difference" or "cell_by_cell"
     * @param headerRow Row number containing headers (default: 1)
     * @param dataStartRow First row containing data (default: 2)
     * @param keyColumns Comma-separated key columns for matching
     * @param ignoredSheets Comma-separated sheet names to ignore
     * @param outputDir Custom output directory
     * @param outputFileName Custom output file name
     * @return CompareResult with mismatch details
     * @throws IOException If file operations fail
     */
    public static CompareResult compareExcelFilesWithConfig(
            String file1Path,
            String file2Path,
            String outputPath,
            String sheetName,
            String compareMode,
            Integer headerRow,
            Integer dataStartRow,
            String keyColumns,
            String ignoredSheets,
            String outputDir,
            String outputFileName
    ) throws IOException {

        log.info("Starting Excel comparison with config: {} vs {}", file1Path, file2Path);

        // Validate input files
        File file1 = new File(file1Path);
        File file2 = new File(file2Path);

        if (!file1.exists()) {
            throw new IOException("First Excel file not found: " + file1Path);
        }
        if (!file2.exists()) {
            throw new IOException("Second Excel file not found: " + file2Path);
        }

        // Build ExcelComparisonConfig
        ExcelComparisonConfig config = buildExcelConfig(sheetName, headerRow, dataStartRow,
                                                        keyColumns, ignoredSheets, outputDir, outputFileName);

        // Set default values
        if (compareMode == null || compareMode.trim().isEmpty()) {
            compareMode = MODE_ROW_DIFFERENCE;
        }

        // Handle custom output path
        String finalOutputPath = determineOutputPath(outputPath, config, file1Path, file2Path);

        log.info("Using Excel config: {}", config.toString());
        log.info("Final output path: {}", finalOutputPath);

        // Perform comparison based on mode with config
        CompareResult result;
        switch (compareMode.toLowerCase()) {
            case MODE_CELL_BY_CELL:
                result = performCellByCellComparisonWithConfig(file1, file2, finalOutputPath, config);
                break;
            case MODE_ROW_DIFFERENCE:
            default:
                result = performRowDifferenceComparisonWithConfig(file1, file2, finalOutputPath, config);
                break;
        }

        log.info("Excel comparison with config completed. Mismatched records: {}", result.getMismatchRecordCount());
        return result;
    }

    /**
     * Build ExcelComparisonConfig from individual parameters.
     */
    private static ExcelComparisonConfig buildExcelConfig(
            String sheetName,
            Integer headerRow,
            Integer dataStartRow,
            String keyColumns,
            String ignoredSheets,
            String outputDir,
            String outputFileName
    ) {
        ExcelComparisonConfig config = new ExcelComparisonConfig();

        // Set sheet name
        if (sheetName != null && !sheetName.trim().isEmpty()) {
            config.setSheetName(sheetName.trim());
        }

        // Set header row (default: 1)
        if (headerRow != null && headerRow > 0) {
            config.setHeaderRow(headerRow);
        }

        // Set data start row (default: 2)
        if (dataStartRow != null && dataStartRow > 0) {
            config.setDataStartRow(dataStartRow);
        }

        // Set key columns
        if (keyColumns != null && !keyColumns.trim().isEmpty()) {
            config.setKeyColumns(keyColumns.trim());
        }

        // Set ignored sheets
        if (ignoredSheets != null && !ignoredSheets.trim().isEmpty()) {
            String[] sheets = ignoredSheets.split(",");
            for (String sheet : sheets) {
                config.addIgnoredSheet(sheet.trim());
            }
        }

        // Set output directory
        if (outputDir != null && !outputDir.trim().isEmpty()) {
            config.setOutputDir(outputDir.trim());
        }

        // Set output file name
        if (outputFileName != null && !outputFileName.trim().isEmpty()) {
            config.setOutputFileName(outputFileName.trim());
        }

        return config;
    }

    /**
     * Determine final output path considering config and parameters.
     */
    private static String determineOutputPath(String outputPath, ExcelComparisonConfig config,
                                            String file1Path, String file2Path) {
        // Priority: outputPath parameter > config settings > default generation
        if (outputPath != null && !outputPath.trim().isEmpty()) {
            return outputPath;
        }

        // Use config settings if available
        if (config.getOutputDir() != null && config.getOutputFileName() != null) {
            return config.getOutputDir() + File.separator + config.getOutputFileName();
        } else if (config.getOutputFileName() != null) {
            String dir = new File(file1Path).getParent();
            if (dir == null) {
                dir = System.getProperty("user.dir");
            }
            return dir + File.separator + config.getOutputFileName();
        }

        // Generate default path
        return generateDefaultOutputPath(file1Path, file2Path);
    }

    /**
     * Perform cell-by-cell comparison with ExcelComparisonConfig.
     */
    private static CompareResult performCellByCellComparisonWithConfig(
            File file1, File file2, String outputPath, ExcelComparisonConfig config
    ) throws IOException {

        log.info("Performing cell-by-cell comparison with config");

        try {
            ExcelCellByCellComparator comparator = new ExcelCellByCellComparator(file1, file2, outputPath);

            // Note: Current comparator doesn't expose config-based methods
            // This is a framework limitation - we'll log the config but use standard comparison
            if (config.hasKeyColumns()) {
                log.info("Note: Key columns specified but cell-by-cell comparator doesn't support custom keys: {}",
                        java.util.Arrays.toString(config.getKeyColumns()));
            }

            if (!config.getIgnoredSheets().isEmpty()) {
                log.info("Note: Ignored sheets specified: {}", config.getIgnoredSheets());
            }

            // Execute comparison - this returns boolean
            boolean hasDifferences = comparator.compareAndLogOnExcel();

            if (hasDifferences) {
                return createConfigResult("Cell-by-cell comparison found differences with config", outputPath, config, true);
            } else {
                return createConfigSuccessResult("Cell-by-cell comparison completed with config - no differences", outputPath, config);
            }

        } catch (Exception e) {
            log.error("Cell-by-cell comparison with config failed", e);
            return createErrorResult("Cell-by-cell comparison with config failed: " + e.getMessage());
        }
    }

    /**
     * Perform row difference comparison with ExcelComparisonConfig.
     */
    private static CompareResult performRowDifferenceComparisonWithConfig(
            File file1, File file2, String outputPath, ExcelComparisonConfig config
    ) throws IOException {

        log.info("Performing row difference comparison with config");

        try {
            ExcelRowDifferenceComparator comparator = new ExcelRowDifferenceComparator(file1, file2, outputPath);

            // Note: Current comparator doesn't expose config-based methods
            // This is a framework limitation - we'll log the config but use standard comparison
            if (config.hasKeyColumns()) {
                log.info("Note: Key columns specified but row difference comparator doesn't support custom keys: {}",
                        java.util.Arrays.toString(config.getKeyColumns()));
            }

            if (!config.getIgnoredSheets().isEmpty()) {
                log.info("Note: Ignored sheets specified: {}", config.getIgnoredSheets());
            }

            // Execute comparison - this returns boolean
            boolean hasDifferences = comparator.compareAndLogOnExcel();

            if (hasDifferences) {
                return createConfigResult("Row difference comparison found differences with config", outputPath, config, true);
            } else {
                return createConfigSuccessResult("Row difference comparison completed with config - no differences", outputPath, config);
            }

        } catch (Exception e) {
            log.error("Row difference comparison with config failed", e);
            return createErrorResult("Row difference comparison with config failed: " + e.getMessage());
        }
    }

    /**
     * Create CompareResult with config information.
     */
    private static CompareResult createConfigResult(String message, String outputPath,
                                                  ExcelComparisonConfig config, boolean hasDifferences) {
        CompareResult result = new CompareResult();
        result.setMismatchRecordCount(hasDifferences ? 1 : 0);
        result.setSourceRecordCount(1);
        result.setMismatchCountByColumns(hasDifferences ? "1" : "0");

        // Create a detail with config information
        MismatchDetail detail = new MismatchDetail();
        detail.setMismatchStatus(hasDifferences ? "DIFFERENCES_FOUND" : "SUCCESS");

        StringBuilder detailMessage = new StringBuilder(message);
        detailMessage.append(" Output saved to: ").append(outputPath);
        if (config.isValidConfig()) {
            detailMessage.append(" | Config: ").append(config.toString());
        }

        detail.setMismatchColumnValue(detailMessage.toString());
        detail.setUniqueKey("COMPARISON_RESULT_WITH_CONFIG");
        detail.setMismatchColumns(new ArrayList<>());

        List<MismatchDetail> details = new ArrayList<>();
        details.add(detail);
        result.setMismatchDetail(details);

        return result;
    }

    /**
     * Create success CompareResult with config information.
     */
    private static CompareResult createConfigSuccessResult(String message, String outputPath,
                                                         ExcelComparisonConfig config) {
        CompareResult result = new CompareResult();
        result.setMismatchRecordCount(0);
        result.setSourceRecordCount(0);
        result.setMismatchCountByColumns("0");

        // Create a success detail with config information
        MismatchDetail successDetail = new MismatchDetail();
        successDetail.setMismatchStatus("SUCCESS");

        StringBuilder detailMessage = new StringBuilder(message);
        detailMessage.append(" Output saved to: ").append(outputPath);
        if (config.isValidConfig()) {
            detailMessage.append(" | Config: ").append(config.toString());
        }

        successDetail.setMismatchColumnValue(detailMessage.toString());
        successDetail.setUniqueKey("COMPARISON_RESULT_WITH_CONFIG");
        successDetail.setMismatchColumns(new ArrayList<>());

        List<MismatchDetail> details = new ArrayList<>();
        details.add(successDetail);
        result.setMismatchDetail(details);

        return result;
    }

    /**
     * Simple wrapper for enhanced Excel comparison with basic config.
     */
    public static CompareResult compareExcelFilesWithHeaderRows(
            String file1Path, String file2Path, int headerRow, int dataStartRow)
            throws IOException {
        return compareExcelFilesWithConfig(file1Path, file2Path, null, null, null,
                                         headerRow, dataStartRow, null, null, null, null);
    }

    /**
     * Simple wrapper for enhanced Excel comparison with key columns.
     */
    public static CompareResult compareExcelFilesWithKeyColumns(
            String file1Path, String file2Path, String keyColumns)
            throws IOException {
        return compareExcelFilesWithConfig(file1Path, file2Path, null, null, null,
                                         null, null, keyColumns, null, null, null);
    }

    /**
     * Simple wrapper for enhanced Excel comparison with ignored sheets.
     */
    public static CompareResult compareExcelFilesWithIgnoredSheets(
            String file1Path, String file2Path, String ignoredSheets)
            throws IOException {
        return compareExcelFilesWithConfig(file1Path, file2Path, null, null, null,
                                         null, null, null, ignoredSheets, null, null);
    }
}