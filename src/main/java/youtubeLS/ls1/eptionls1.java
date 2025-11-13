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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Класс для построчной отправки данных из Excel в Naumen Service Desk
 * через REST-метод exec-post.
 *
 * Формат:
 *   - первая строка Excel — заголовок (имена полей),
 *   - каждая следующая непустая строка — один JSON-объект,
 *   - каждая строка отправляется отдельным POST-запросом.
 *
 * На стороне Naumen строка JSON попадает в параметр "params" и
 * может разбира́ться так:
 *
 *   def obj = new JsonSlurper().parseText(params)
 */
public class ExcelToNaumenUploader {

    // ======================== НАСТРОЙКИ ===============================

    /**
     * Путь к Excel-файлу (.xlsx).
     * Важно: файл должен существовать и быть доступен для чтения.
     */
    private static final String EXCEL_FILE_PATH = "C:/data/equipment.xlsx";

    /**
     * Базовый URL REST-метода exec-post БЕЗ параметров.
     *
     * Для Naumen SD это обычно что-то вроде:
     *   http://<ip или имя>:8080/sd/services/rest/exec-post
     *
     * ВНИМАНИЕ: только http://, т.к. у тебя нет SSL-сертификата.
     */
    private static final String NAUMEN_API_URL =
            "http://172.16.3.107:8080/sd/services/rest/exec-post";

    /**
     * Ключ доступа к REST API Naumen.
     *
     * В проде его НЕ хардкодят в коде:
     * - переменная окружения,
     * - внешний конфиг и т.п.
     * Здесь для простоты — как константа-заглушка.
     */
    private static final String ACCESS_KEY = "ВАШ_ACCESS_KEY";

    /**
     * Имя вызываемой функции на стороне Naumen.
     * В твоём случае: modules.apiMigrationITop.migration
     */
    private static final String FUNCTION = "modules.apiMigrationITop.migration";

    /**
     * Пауза между запросами (мс), чтобы не перегружать сервер.
     * Можно увеличить, если сервер слабый.
     */
    private static final long PAUSE_MS = 500L;

    /**
     * Включить подробный вывод (JSON, ответы сервера).
     * Для прод-среды можно выключить (false), чтобы не светить данные.
     */
    private static final boolean VERBOSE = true;

    /**
     * Общий экземпляр Gson для сериализации объектов в JSON.
     * disableHtmlEscaping — чтобы не превращать <>& в \uXXXX, но
     * это не ломает JSON, только делает его более читаемым.
     */
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    // ======================== MAIN ====================================

    public static void main(String[] args) {
        List<String> jsonObjects;
        try {
            jsonObjects = readExcelFile(EXCEL_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Ошибка чтения Excel-файла: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (jsonObjects.isEmpty()) {
            System.out.println("Нет данных для отправки (все строки пустые или только заголовок).");
            return;
        }

        System.out.println("Найдено строк к отправке: " + jsonObjects.size());

        for (int i = 0; i < jsonObjects.size(); i++) {
            String json = jsonObjects.get(i);

            // в Excel: строка 0 — заголовок, значит первая строка с данными — №2
            int excelRowNum = i + 2;

            if (VERBOSE) {
                System.out.println();
                System.out.println("[" + (i + 1) + "/" + jsonObjects.size() + "] " +
                        "Отправка строки Excel № " + excelRowNum);
                System.out.println("JSON: " + json);
            }

            try {
                // Собираем URL с параметрами accessKey и func
                String urlStr = NAUMEN_API_URL
                        + "?accessKey=" + URLEncoder.encode(ACCESS_KEY, StandardCharsets.UTF_8.toString())
                        + "&func=" + URLEncoder.encode(FUNCTION, StandardCharsets.UTF_8.toString());

                if (VERBOSE) {
                    // Специально НЕ печатаем токен в URL.
                    System.out.println("URL (без параметров доступа): " + NAUMEN_API_URL);
                }

                String response = sendJsonRequest(urlStr, json);
                System.out.println("Ответ сервера: " + response);

            } catch (IOException e) {
                System.err.println("Ошибка отправки для строки Excel № " + excelRowNum + ": " + e.getMessage());
                e.printStackTrace();
            }

            // Пауза между запросами, чтобы не забивать сервер
            try {
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Поток прерван, останавливаемся.");
                break;
            }
        }

        System.out.println("Завершено.");
    }

    // ===================== ЧТЕНИЕ EXCEL ================================

    /**
     * Читает Excel-файл формата .xlsx.
     * Первая строка (row 0) используется как заголовок (имена полей).
     * Каждая следующая НЕпустая строка превращается в JSON-объект.
     *
     * @return список JSON-строк (по одной на каждую строку Excel с данными)
     */
    private static List<String> readExcelFile(String filePath) throws IOException {
        List<String> jsonList = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                System.err.println("В книге нет ни одного листа.");
                return jsonList;
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                System.err.println("Отсутствует строка заголовка (row 0).");
                return jsonList;
            }

            int numCols = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>(numCols);

            // Заголовки колонок => имена полей JSON
            for (int c = 0; c < numCols; c++) {
                Cell cell = headerRow.getCell(c);
                String name = (cell != null) ? cell.toString().trim() : ("Column" + (c + 1));
                headers.add(name);
            }

            // Обходим строки с индексом 1..lastRowNum
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                // Проверяем, не полностью ли пустая строка
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
                if (allEmpty) {
                    continue;
                }

                Map<String, Object> rowData = new LinkedHashMap<>();

                for (int c = 0; c < numCols; c++) {
                    String key = headers.get(c);
                    Cell cell = row.getCell(c);

                    Object value;

                    if (cell == null || cell.getCellType() == CellType.BLANK) {
                        // Пустая ячейка — пустая строка
                        value = "";
                    } else {
                        switch (cell.getCellType()) {
                            case STRING:
                                value = cell.getStringCellValue().trim();
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    // Ячейка с датой — переводим в строку
                                    value = cell.getDateCellValue().toString();
                                } else {
                                    // Обычное число — double
                                    value = cell.getNumericCellValue();
                                }
                                break;
                            case BOOLEAN:
                                value = cell.getBooleanCellValue();
                                break;
                            case FORMULA:
                                // Проще всего — формула как текст.
                                // Если надо реально считать — использовать FormulaEvaluator.
                                value = cell.getCellFormula();
                                break;
                            default:
                                // На всякий случай — строковое представление
                                value = cell.toString().trim();
                        }
                    }

                    rowData.put(key, value);
                }

                // Вариант 1. На сервере ждут ОДИН объект:
                String json = GSON.toJson(rowData);

                // Вариант 2. Если на стороне Naumen ждут МАССИВ (даже из одной строки),
                // то можно вместо строки выше использовать такую:
                //
                // String json = GSON.toJson(java.util.List.of(rowData));
                //
                // Тогда на Groovy будет List, а не Map.

                jsonList.add(json);
            }
        }

        return jsonList;
    }

    // ===================== ОТПРАВКА В NAUMEN ===========================

    /**
     * Отправляет JSON-строку на указанный URL методом POST.
     * Тело запроса — форма: "params=<URL-кодированный JSON>".
     *
     * На стороне Naumen:
     *   def params = request.getParameter("params")
     *   def obj = new JsonSlurper().parseText(params)
     */
    private static String sendJsonRequest(String urlStr, String json) throws IOException {
        // Готовим тело формы: params=<urlencoded json>
        String encodedJson = URLEncoder.encode(json, StandardCharsets.UTF_8.toString());
        String formBody = "params=" + encodedJson;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Используем только POST (GET для exec-post не поддерживается)
        conn.setRequestMethod("POST");

        // Формат тела — application/x-www-form-urlencoded с указанием UTF-8
        conn.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
        );

        conn.setDoOutput(true);

        // Записываем тело запроса
        try (OutputStream os = conn.getOutputStream()) {
            byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
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

        if (code == 500) {
            return "HTTP 500 Internal Server Error. Тело ответа: " + responseBody;
        }
        return "HTTP " + code + ". Тело ответа: " + responseBody;
    }
}