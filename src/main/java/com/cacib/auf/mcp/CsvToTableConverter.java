package com.cacib.auf.mcp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import com.cacib.auf.core.csv.AfCsvReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to convert CSV files to table format (List<Map<String,String>>)
 * required by ATableComparisonCTRL for comparison operations.
 */
public class CsvToTableConverter {

    private static final Logger log = LoggerFactory.getLogger(CsvToTableConverter.class);

    /**
     * Convert CSV file to List<Map<String,String>> format for table comparison.
     *
     * @param filePath Path to the CSV file
     * @param delimiter Delimiter used in the CSV file
     * @param skipHeader Whether to skip the header row
     * @param uniqueKeyColumn Name of the unique key column (comma-separated for multi-column keys)
     * @return List of maps representing table rows
     * @throws FileNotFoundException If the CSV file is not found
     * @throws IOException If there's an error reading the file
     */
    public static List<Map<String, String>> convertCsvToTable(
            String filePath,
            String delimiter,
            boolean skipHeader,
            String uniqueKeyColumn
    ) throws FileNotFoundException, IOException {

        log.info("Converting CSV to table format: {}", filePath);

        File csvFile = new File(filePath);
        if (!csvFile.exists()) {
            throw new FileNotFoundException("CSV file not found: " + filePath);
        }

        AfCsvReader csvReader = new AfCsvReader();
        List<String> csvLines = csvReader.readCSVAsStringList(csvFile, "\\n", false);

        if (csvLines.isEmpty()) {
            log.warn("CSV file is empty: {}", filePath);
            return new ArrayList<>();
        }

        String[] headers;
        int dataStartIndex;

        if (skipHeader && csvLines.size() > 1) {
            headers = extractHeaders(csvLines.get(0), delimiter);
            dataStartIndex = 1;
        } else {
            // Generate default headers if no header row
            String firstLine = csvLines.get(0);
            int columnCount = firstLine.split(delimiter, -1).length;
            headers = generateDefaultHeaders(columnCount);
            dataStartIndex = 0;
        }

        // Parse multi-column keys and validate
        List<String> keyColumns = parseKeyColumns(uniqueKeyColumn);
        validateKeyColumns(headers, keyColumns);

        // Add composite key column if multi-column key specified
        if (keyColumns.size() > 1) {
            headers = addCompositeKeyColumn(headers);
        }

        List<Map<String, String>> tableData = new ArrayList<>();

        for (int i = dataStartIndex; i < csvLines.size(); i++) {
            String line = csvLines.get(i).trim();
            if (!line.isEmpty()) {
                Map<String, String> rowMap = parseLineToMap(line, headers, delimiter, keyColumns, i);
                tableData.add(rowMap);
            }
        }

        log.info("Converted {} rows to table format", tableData.size());
        return tableData;
    }

    /**
     * Parse a CSV line into a map with column headers as keys.
     */
    private static Map<String, String> parseLineToMap(
            String line,
            String[] headers,
            String delimiter,
            List<String> keyColumns,
            int rowIndex
    ) {
        Map<String, String> rowMap = new LinkedHashMap<>();
        String[] values = line.split(delimiter, -1);

        for (int i = 0; i < headers.length; i++) {
            String value = "";
            if (i < values.length) {
                value = values[i].trim();
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                    value = value.substring(1, value.length() - 1);
                }
            }
            rowMap.put(headers[i], value);
        }

        // Handle composite key creation for multi-column keys
        if (keyColumns.size() > 1) {
            StringBuilder compositeKey = new StringBuilder();
            for (int i = 0; i < keyColumns.size(); i++) {
                String keyCol = keyColumns.get(i);
                String keyValue = rowMap.get(keyCol);
                if (keyValue == null || keyValue.isEmpty()) {
                    keyValue = "NULL_" + i;
                }
                compositeKey.append(keyValue);
                if (i < keyColumns.size() - 1) {
                    compositeKey.append("|"); // Use pipe as separator
                }
            }
            rowMap.put("_COMPOSITE_KEY_", compositeKey.toString());
        } else if (keyColumns.size() == 1) {
            // Ensure single key column has a value
            String singleKey = keyColumns.get(0);
            if (!rowMap.containsKey(singleKey) || rowMap.get(singleKey).isEmpty()) {
                rowMap.put(singleKey, "ROW_" + rowIndex);
            }
        }

        return rowMap;
    }

    /**
     * Extract headers from the first line of CSV.
     */
    private static String[] extractHeaders(String firstLine, String delimiter) {
        String[] rawHeaders = firstLine.split(delimiter, -1);
        String[] cleanHeaders = new String[rawHeaders.length];

        for (int i = 0; i < rawHeaders.length; i++) {
            String header = rawHeaders[i].trim();
            // Remove quotes if present
            if (header.startsWith("\"") && header.endsWith("\"") && header.length() > 1) {
                header = header.substring(1, header.length() - 1);
            }
            cleanHeaders[i] = header.isEmpty() ? "COLUMN_" + (i + 1) : header;
        }

        return cleanHeaders;
    }

    /**
     * Generate default headers when no header row is present.
     */
    private static String[] generateDefaultHeaders(int columnCount) {
        String[] headers = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            headers[i] = "COLUMN_" + (i + 1);
        }
        return headers;
    }

    /**
     * Parse comma-separated key column string into list.
     */
    private static List<String> parseKeyColumns(String uniqueKeyColumn) {
        List<String> keyColumns = new ArrayList<>();
        if (uniqueKeyColumn != null && !uniqueKeyColumn.trim().isEmpty()) {
            String[] keys = uniqueKeyColumn.split(",");
            for (String key : keys) {
                String trimmedKey = key.trim();
                if (!trimmedKey.isEmpty()) {
                    keyColumns.add(trimmedKey);
                }
            }
        }
        return keyColumns;
    }

    /**
     * Validate that all key columns exist in headers.
     */
    private static void validateKeyColumns(String[] headers, List<String> keyColumns) throws IOException {
        Set<String> headerSet = new HashSet<>(Arrays.asList(headers));
        for (String keyCol : keyColumns) {
            if (!headerSet.contains(keyCol)) {
                throw new IOException("Key column '" + keyCol + "' not found in CSV headers: " + Arrays.toString(headers));
            }
        }
    }

    /**
     * Add composite key column to headers for multi-column keys.
     */
    private static String[] addCompositeKeyColumn(String[] headers) {
        String[] newHeaders = new String[headers.length + 1];
        newHeaders[0] = "_COMPOSITE_KEY_";
        System.arraycopy(headers, 0, newHeaders, 1, headers.length);
        return newHeaders;
    }

    /**
     * Get the effective unique key column name for table comparison.
     * Returns composite key name for multi-column keys.
     */
    public static String getEffectiveKeyColumn(String uniqueKeyColumn) {
        if (uniqueKeyColumn == null || uniqueKeyColumn.trim().isEmpty()) {
            return "ID"; // Default fallback
        }

        List<String> keyColumns = parseKeyColumns(uniqueKeyColumn);
        if (keyColumns.size() > 1) {
            return "_COMPOSITE_KEY_";
        } else {
            return keyColumns.get(0);
        }
    }
}