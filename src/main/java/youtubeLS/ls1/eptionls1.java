import org.apache.poi.ss.usermodel.*;         // Apache POI classes for Excel
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DateUtil;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.gson.Gson;

public class ExcelToNaumenUploader {
    // Constants (replace with actual values as needed)
    private static final String EXCEL_FILE_PATH = "C:/data/equipment.xlsx";
    private static final String NAUMEN_API_URL = "http://your-server/naumen/exec-post";  
    private static final String ACCESS_KEY = "YOUR_ACCESS_KEY";  
    private static final String FUNCTION = "modules.apiMigrationITop.migration";  // example function name

    public static void main(String[] args) {
        List<String> jsonObjects;
        try {
            // Read Excel file and convert rows to JSON strings
            jsonObjects = readExcelFile(EXCEL_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Error reading Excel file: " + e.getMessage());
            return;
        }

        int total = jsonObjects.size();
        for (int i = 0; i < total; i++) {
            String json = jsonObjects.get(i);
            // Calculate actual Excel row number (data starts from row 2 since row 1 is header)
            int excelRowNum = i + 2;
            System.out.println("\n[" + (i+1) + "/" + total + "] Sending JSON for Excel row " + excelRowNum);
            System.out.println("JSON: " + json);
            try {
                // Build URL with accessKey and func parameters
                String urlStr = NAUMEN_API_URL + "?accessKey=" + URLEncoder.encode(ACCESS_KEY, "UTF-8")
                                 + "&func=" + URLEncoder.encode(FUNCTION, "UTF-8");
                System.out.println("URL: " + urlStr);
                // Send HTTP POST request with the JSON data
                String response = sendJsonRequest(urlStr, json);
                System.out.println("Response: " + response);
            } catch (IOException e) {
                System.err.println("Error sending request for row " + excelRowNum + ": " + e.getMessage());
            }

            // Pause between requests to avoid overloading the server
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Reads the Excel file and converts each non-empty row (after header) into a JSON string.
     */
    private static List<String> readExcelFile(String filePath) throws IOException {
        List<String> jsonList = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return jsonList; // no sheet found
            }
            // Read header row (row 0) to get field names
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return jsonList; // empty sheet
            }
            int numCols = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < numCols; c++) {
                Cell headerCell = headerRow.getCell(c);
                String headerName = (headerCell != null) ? headerCell.getStringCellValue().trim() : ("Column" + (c+1));
                headers.add(headerName);
            }

            // Iterate through each subsequent row and build JSON objects
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue; // skip missing/empty row
                }
                // Check if the row is completely empty
                boolean isEmpty = true;
                for (int c = 0; c < numCols; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().trim().isEmpty()) {
                        isEmpty = false;
                        break;
                    }
                }
                if (isEmpty) {
                    continue; // skip empty rows
                }

                // Create a map for this row's data
                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int c = 0; c < numCols; c++) {
                    String key = headers.get(c);
                    Cell cell = row.getCell(c);
                    if (cell == null || cell.getCellType() == CellType.BLANK) {
                        rowData.put(key, ""); // empty cell -> empty string
                        continue;
                    }
                    switch (cell.getCellType()) {
                        case STRING:
                            rowData.put(key, cell.getStringCellValue().trim());
                            break;
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
                                // If cell is a date, format it to string (e.g., ISO 8601 or default)
                                rowData.put(key, cell.getDateCellValue().toString());
                            } else {
                                // Numeric value (not a date)
                                rowData.put(key, cell.getNumericCellValue());
                            }
                            break;
                        case BOOLEAN:
                            rowData.put(key, cell.getBooleanCellValue());
                            break;
                        case FORMULA:
                            // Store the formula expression as a string (could evaluate if needed)
                            rowData.put(key, cell.getCellFormula());
                            break;
                        default:
                            // Other types (including errors), treat as string
                            rowData.put(key, cell.toString().trim());
                    }
                }
                // Convert the row data map to JSON string
                String json = new Gson().toJson(rowData);
                jsonList.add(json);
            }
        }
        return jsonList;
    }

    /**
     * Sends a JSON object to the given URL using an HTTP POST request (application/x-www-form-urlencoded).
     * The JSON is sent as the value of the "params" form parameter.
     * Returns the response body as a string.
     */
    private static String sendJsonRequest(String urlStr, String json) throws IOException {
        // Prepare URL-encoded form data
        String encodedJson = URLEncoder.encode(json, StandardCharsets.UTF_8.toString());
        String urlParameters = "params=" + encodedJson;

        // Open connection to the URL
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setDoOutput(true);

        // Send the form data in the request body
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = urlParameters.getBytes(StandardCharsets.UTF_8);
            os.write(input);
        }

        // Get response code and input stream (error stream for HTTP 4xx/5xx)
        int responseCode = conn.getResponseCode();
        InputStream responseStream = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
        // Read the response from the stream
        String responseBody;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            responseBody = br.lines().collect(Collectors.joining("\n"));
        }

        conn.disconnect();
        // If HTTP 500, include that info in the returned string (could also throw exception or handle separately)
        if (responseCode == 500) {
            return "HTTP 500 Internal Server Error. Response: " + responseBody;
        }
        return "HTTP " + responseCode + " Response: " + responseBody;
    }
}