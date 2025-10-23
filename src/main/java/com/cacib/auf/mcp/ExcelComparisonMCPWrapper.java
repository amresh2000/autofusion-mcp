package com.cacib.auf.mcp;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cacib.auf.core.comparison.excel.ExcelComparisonEngine;
import com.cacib.auf.core.comparison.excel.ExcelReportGenerator;
import com.cacib.auf.core.comparison.model.CompareResult;

/**
 * Simplified MCP wrapper for Excel comparison functionality.
 * Uses unified ExcelComparisonEngine + AfTableComparisonCTRLV2 + AfExcelReader.
 * Always generates 4-sheet Excel reports with comprehensive comparison data.
 */
public class ExcelComparisonMCPWrapper {

    private static final Logger log = LoggerFactory.getLogger(ExcelComparisonMCPWrapper.class);

    private static final ExcelComparisonEngine engine = new ExcelComparisonEngine();
    private static final ExcelReportGenerator reportGenerator = new ExcelReportGenerator();

    // Default values
    private static final String DEFAULT_UNIQUE_KEY = "ID";
    private static final String DEFAULT_SHEET_NAME = "Sheet1";

    /**
     * Unified Excel comparison method with mandatory 4-sheet Excel report generation.
     * Uses AfExcelReader + AfTableComparisonCTRLV2 for all comparisons.
     *
     * @param file1Path Path to first Excel file
     * @param file2Path Path to second Excel file
     * @param uniqueKey Column name to use as unique key for row matching
     * @param thresholds Map of column names to percentage thresholds for numeric comparisons
     * @param ignoreColumns Set of column names to ignore during comparison
     * @param outputPath Custom output path for Excel report
     * @param sourceSheetName Name of sheet in source file
     * @param targetSheetName Name of sheet in target file
     * @return CompareResult with comprehensive mismatch details and Excel report generated
     * @throws IOException If file operations fail
     */
    public static CompareResult compareExcelFiles(
            String file1Path,
            String file2Path,
            String uniqueKey,
            Map<String, Double> thresholds,
            Set<String> ignoreColumns,
            String outputPath,
            String sourceSheetName,
            String targetSheetName
    ) throws IOException {

        log.info("Starting unified Excel comparison: {} vs {}", file1Path, file2Path);

        // Set defaults
        String finalUniqueKey = (uniqueKey != null && !uniqueKey.trim().isEmpty()) ? uniqueKey : DEFAULT_UNIQUE_KEY;
        String finalSourceSheet = (sourceSheetName != null && !sourceSheetName.trim().isEmpty()) ? sourceSheetName : DEFAULT_SHEET_NAME;
        String finalTargetSheet = (targetSheetName != null && !targetSheetName.trim().isEmpty()) ? targetSheetName : DEFAULT_SHEET_NAME;

        log.info("Using unified architecture: UniqueKey='{}', SourceSheet='{}', TargetSheet='{}'",
                finalUniqueKey, finalSourceSheet, finalTargetSheet);

        try {
            // Perform Excel comparison using unified engine
            CompareResult result;

            // Get effective key column (handles composite keys)
            String effectiveUniqueKey = getEffectiveUniqueKey(finalUniqueKey);

            // Use the main compareExcelFiles method which handles all cases
            log.info("Excel comparison using unified method with effective key: {}", effectiveUniqueKey);
            result = engine.compareExcelFiles(
                    file1Path, file2Path, finalSourceSheet, finalTargetSheet,
                    effectiveUniqueKey, thresholds, ignoreColumns);

            // Set metadata for Excel report
            result.setSourceType("Excel");
            result.setTargetType("Excel");
            result.setSourceLocation(file1Path + " (Sheet: " + finalSourceSheet + ")");
            result.setTargetLocation(file2Path + " (Sheet: " + finalTargetSheet + ")");

            // Always generate 4-sheet Excel report
            String reportPath = determineReportPath(outputPath, file1Path, file2Path);
            reportGenerator.generateComparisonReport(result, reportPath);
            log.info("4-sheet Excel report generated: {}", reportPath);

            return result;

        } catch (Exception e) {
            log.error("Excel comparison failed", e);
            throw new IOException("Excel comparison failed: " + e.getMessage(), e);
        }
    }

    /**
     * Simplified Excel comparison with minimal parameters.
     * Uses default settings and generates 4-sheet report.
     */
    public static CompareResult compareExcelFilesSimple(String file1Path, String file2Path, String outputPath)
            throws IOException {
        return compareExcelFiles(file1Path, file2Path, null, null, null, outputPath, null, null);
    }

    /**
     * Excel comparison with custom unique key.
     */
    public static CompareResult compareExcelFilesWithUniqueKey(
            String file1Path, String file2Path, String uniqueKey, String outputPath)
            throws IOException {
        return compareExcelFiles(file1Path, file2Path, uniqueKey, null, null, outputPath, null, null);
    }

    /**
     * Excel comparison with thresholds for numeric columns.
     */
    public static CompareResult compareExcelFilesWithThresholds(
            String file1Path, String file2Path, String uniqueKey, Map<String, Double> thresholds, String outputPath)
            throws IOException {
        return compareExcelFiles(file1Path, file2Path, uniqueKey, thresholds, null, outputPath, null, null);
    }

    /**
     * Excel comparison with ignore columns.
     */
    public static CompareResult compareExcelFilesWithIgnoreColumns(
            String file1Path, String file2Path, String uniqueKey, Set<String> ignoreColumns, String outputPath)
            throws IOException {
        return compareExcelFiles(file1Path, file2Path, uniqueKey, null, ignoreColumns, outputPath, null, null);
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

            return baseName1 + "_vs_" + baseName2 + "_excel_comparison_report.xlsx";
        }
    }

    /**
     * Get the effective unique key for Excel comparison.
     * For multi-column keys, autofusion ExcelComparisonEngine handles composite keys internally.
     * We pass the original comma-separated string and let the engine handle it.
     */
    private static String getEffectiveUniqueKey(String uniqueKey) {
        if (uniqueKey == null || uniqueKey.trim().isEmpty()) {
            return DEFAULT_UNIQUE_KEY; // "ID"
        }
        return uniqueKey.trim();
    }
}