import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DateUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Построчная отправка данных из Excel в Naumen Service Desk
 * через REST-метод exec-post.
 *
 * ВАЖНО:
 *   - на стороне Naumen есть метод:
 *
 *        def migration(requestContent) {
 *            def obj = new JsonSlurper().parseText(requestContent)
 *            for (ob in obj) { ... }
 *        }
 *
 *   - поэтому мы:
 *       1) вызываем exec-post с params=requestContent,
 *       2) отправляем JSON в теле запроса (raw body),
 *       3) Content-Type = application/json; charset=UTF-8,
 *       4) в requestContent кладём МАССИВ, даже если в нём один объект: [ { ... } ].
 */
public class ExcelToNaumenUploader {

    // ======================== НАСТРОЙКИ ===============================

    /**
     * Путь к Excel-файлу (.xlsx).
     */
    private static final String EXCEL_FILE_PATH = "C:/data/equipment.xlsx";

    /**
     
     */
    private static final String NAUMEN_API_URL =
            "
     
    private static final String ACCESS_KEY = "ВАШ_ACCESS_KEY";


    private static final String FUNCTION = "";

    /**
     * Пауза между запросами (мс), чтобы не класть сервер.
     */
    private static final long PAUSE_MS = 500L;

    /**
     * Флаг подробного логирования.
     * В бою можно выключить (false), чтобы не светить данные.
     */
    private static final boolean VERBOSE = true;

    /**
     * Общий экземпляр Gson для сериализации JSON.
     */
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping() // делаем JSON чуть читаемее, спецсимволы всё равно экранируются как положено
            .create();

    // ======================== MAIN ====================================

    public static void main(String[] args) {
        List<Map<String, Object>> rows;
        try {
            rows = readExcelRows(EXCEL_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Ошибка чтения Excel-файла: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (rows.isEmpty()) {
            System.out.println("Нет данных для отправки (все строки пустые или только заголовок).");
            return;
        }

        System.out.println("Найдено строк к отправке: " + rows.size());

        for (int i = 0; i < rows.size(); i++) {

            Map<String, Object> rowData = rows.get(i);
            int excelRowNum = i + 2; // 1-я строка — заголовок, первая строка данных — №2

            // ДЕЛАЕМ МАССИВ ИЗ ОДНОЙ СТРОКИ: [ { ... } ]
            String jsonArray = GSON.toJson(Collections.singletonList(rowData));

            if (VERBOSE) {
                System.out.println();
                System.out.println("[" + (i + 1) + "/" + rows.size() + "] " +
                        "Отправка строки Excel № " + excelRowNum);
                System.out.println("JSON (requestContent): " + jsonArray);
            }

            try {
                String urlStr = buildExecPostUrl();

                if (VERBOSE) {
                    System.out.println("URL (без тела): " + urlStr);
                }

                HttpResult result = postJsonBody(urlStr, jsonArray);
                System.out.println("Ответ сервера: " + result);

                // при 500 логируем отдельно, чтобы можно было показать разработчикам Naumen
                if (result.code == 500) {
                    logFailedRow(excelRowNum, jsonArray, result.body);
                }

            } catch (IOException e) {
                System.err.println("Ошибка отправки для строки Excel № " + excelRowNum + ": " + e.getMessage());
                e.printStackTrace();
            }

            try {
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Поток прерван, остановка.");
                break;
            }
        }

        System.out.println("Завершено.");
    }

    // ===================== ПОСТРОЕНИЕ URL ==============================

    /**
     * Собираем URL вида:
     *   http://.../exec-post?accessKey=...&func=...&params=requestContent
     *
     * Т.е. exec-post понимает, что requestContent надо взять из тела POST-запроса.
     */
    private static String buildExecPostUrl() throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder(NAUMEN_API_URL);
        char join = NAUMEN_API_URL.contains("?") ? '&' : '?';

        sb.append(join)
          .append("accessKey=").append(URLEncoder.encode(ACCESS_KEY, StandardCharsets.UTF_8.toString()))
          .append("&func=").append(URLEncoder.encode(FUNCTION, StandardCharsets.UTF_8.toString()))
          .append("&params=").append(URLEncoder.encode("requestContent", StandardCharsets.UTF_8.toString()));

        return sb.toString();
    }

    // ===================== ЧТЕНИЕ EXCEL ================================

    /**
     * Читает Excel (.xlsx) и возвращает список строк в виде Map "имя_столбца -> значение".
     * Первая строка используется как заголовок (имена полей).
     */
    private static List<Map<String, Object>> readExcelRows(String filePath) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                System.err.println("В книге нет листов.");
                return rows;
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                System.err.println("Нет строки с заголовком (row 0).");
                return rows;
            }

            int numCols = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>(numCols);

            // Заголовки колонок — имена полей
            for (int c = 0; c < numCols; c++) {
                Cell cell = headerRow.getCell(c);
                String name = (cell != null) ? cell.toString().trim() : ("Column" + (c + 1));
                headers.add(name);
            }

            // Перебор строк с индексом 1..lastRowNum
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // пропускаем полностью пустые строки
                boolean allEmpty = true;
                for (int c = 0; c < numCols; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null
                            && cell.getCellType() != CellType.BLANK
                            && !cell.toString().trim().isEmpty()) {
                        allEmpty = false;
                        break;
                    }
                }
                if (allEmpty) continue;

                Map<String, Object> rowData = new LinkedHashMap<>();

                for (int c = 0; c < numCols; c++) {
                    String key = headers.get(c);
                    Cell cell = row.getCell(c);

                    Object value;

                    if (cell == null || cell.getCellType() == CellType.BLANK) {
                        value = "";
                    } else {
                        switch (cell.getCellType()) {
                            case STRING:
                                value = cell.getStringCellValue().trim();
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    value = cell.getDateCellValue().toString();
                                } else {
                                    value = cell.getNumericCellValue();
                                }
                                break;
                            case BOOLEAN:
                                value = cell.getBooleanCellValue();
                                break;
                            case FORMULA:
                                value = cell.getCellFormula();
                                break;
                            default:
                                value = cell.toString().trim();
                        }
                    }

                    rowData.put(key, value);
                }

                rows.add(rowData);
            }
        }

        return rows;
    }

    // ===================== HTTP POST JSON ==============================

    /** Результат HTTP-запроса: код + тело. */
    private static class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }

        @Override
        public String toString() {
            return "HTTP " + code + ". Тело: " + body;
        }
    }

    /**
     * Отправляет JSON в теле POST-запроса.
     * Content-Type: application/json; charset=UTF-8.
     * JSON попадает в аргумент requestContent у groovy-метода.
     */
    private static HttpResult postJsonBody(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        // пишем JSON в тело запроса
        try (OutputStream os = conn.getOutputStream()) {
            byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(bytes);
            os.flush();
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();

        String responseBody = "";
        if (is != null) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                responseBody = br.lines().collect(Collectors.joining("\n"));
            }
        }

        conn.disconnect();
        return new HttpResult(code, responseBody);
    }

    // ===================== ЛОГИРОВАНИЕ ПРОБЛЕМНЫХ СТРОК ==================

    /**
     * Если сервер вернул 500, сохраняем JSON и ответ в файл failed_rows.log.
     * Это удобно показать разработчикам Naumen.
     */
    private static void logFailedRow(int excelRowNum, String json, String responseBody) {
        File logFile = new File("failed_rows.log");
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.println("==== Excel row " + excelRowNum + " ====");
            out.println("JSON отправленный (requestContent):");
            out.println(json);
            out.println("Ответ сервера (500):");
            out.println(responseBody);
            out.println();

        } catch (IOException e) {
            System.err.println("Не удалось записать failed_rows.log: " + e.getMessage());
        }
    }
}