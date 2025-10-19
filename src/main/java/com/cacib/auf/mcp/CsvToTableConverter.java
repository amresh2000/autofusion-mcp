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
     * @param uniqueKeyColumn Name of the unique key column (will be added if not present)
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

        // Ensure unique key column exists in headers
        headers = ensureUniqueKeyColumn(headers, uniqueKeyColumn);

        List<Map<String, String>> tableData = new ArrayList<>();

        for (int i = dataStartIndex; i < csvLines.size(); i++) {
            String line = csvLines.get(i).trim();
            if (!line.isEmpty()) {
                Map<String, String> rowMap = parseLineToMap(line, headers, delimiter, uniqueKeyColumn, i);
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
            String uniqueKeyColumn,
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

        // Ensure unique key column has a value
        if (!rowMap.containsKey(uniqueKeyColumn) || rowMap.get(uniqueKeyColumn).isEmpty()) {
            rowMap.put(uniqueKeyColumn, "ROW_" + rowIndex);
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
     * Ensure the unique key column exists in the headers array.
     */
    private static String[] ensureUniqueKeyColumn(String[] headers, String uniqueKeyColumn) {
        // Check if unique key column already exists
        for (String header : headers) {
            if (header.equals(uniqueKeyColumn)) {
                return headers; // Already exists
            }
        }

        // Add unique key column if it doesn't exist
        String[] newHeaders = new String[headers.length + 1];
        newHeaders[0] = uniqueKeyColumn;
        System.arraycopy(headers, 0, newHeaders, 1, headers.length);
        return newHeaders;
    }
}