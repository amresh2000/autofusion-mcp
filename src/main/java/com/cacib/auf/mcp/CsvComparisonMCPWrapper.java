package com.cacib.auf.mcp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cacib.auf.core.comparison.model.CompareResult;
import com.cacib.auf.core.comparison.csv.CSVComparisonEngine;
import com.cacib.auf.core.comparison.excel.ExcelReportGenerator;

/**
 * Simplified MCP wrapper for CSV comparison functionality.
 * Uses CSVComparisonEngine for all comparisons and always generates 4-sheet Excel reports.
 * This is a thin wrapper that delegates to the core CSV comparison engine.
 */
public class CsvComparisonMCPWrapper {

    private static final Logger log = LoggerFactory.getLogger(CsvComparisonMCPWrapper.class);

    // CSV comparison engine for all operations
    private static final CSVComparisonEngine csvEngine = new CSVComparisonEngine();

    // Excel report generator for 4-sheet reports
    private static final ExcelReportGenerator reportGenerator = new ExcelReportGenerator();

    /**
     * Unified CSV comparison method with mandatory 4-sheet Excel report generation.
     * Uses CSVComparisonEngine for all comparisons with automatic delimiter detection.
     *
     * @param file1Path Path to first CSV file
     * @param file2Path Path to second CSV file
     * @param delimiter1 Delimiter for first CSV file (null for auto-detection)
     * @param delimiter2 Delimiter for second CSV file (null for auto-detection)
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

        log.info("Starting CSV comparison using CSVComparisonEngine: {} vs {}", file1Path, file2Path);

        // Convert ignore columns string to Set if provided
        Set<String> ignoreColumnsSet = null;
        if (ignoreColumns != null && !ignoreColumns.trim().isEmpty()) {
            ignoreColumnsSet = new HashSet<>();
            String[] columns = ignoreColumns.split(",");
            for (String column : columns) {
                String trimmed = column.trim();
                if (!trimmed.isEmpty()) {
                    ignoreColumnsSet.add(trimmed);
                }
            }
        }

        // Delegate to CSVComparisonEngine for the actual comparison
        CompareResult result = csvEngine.compareCsvFiles(
                file1Path, file2Path,
                delimiter1, delimiter2,
                skipHeader1, skipHeader2,
                uniqueKey, thresholds, ignoreColumnsSet);

        // Always generate 4-sheet Excel report
        String reportPath = determineReportPath(outputPath, file1Path, file2Path);
        reportGenerator.generateComparisonReport(result, reportPath);
        log.info("4-sheet Excel report generated: {}", reportPath);

        return result;
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