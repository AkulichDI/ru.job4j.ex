import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

public class ExcelRowPoster {

    // === НАСТРОЙКИ ===
    static String EXCEL_FILE     = "C:/data/equipment.xlsx";

    static String BASE_EXEC_POST = "";
    static String ACCESS_KEY     = "PASTE_YOUR_ACCESS_KEY";
    static String FUNC           = "";

    static long   PAUSE_MS       = 400;   // мягкая пауза между строками
    static boolean TRUST_ALL_SSL = false; // true только если BASE_EXEC_POST начинается с https:// и нужен аналог curl -k

    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(String[] args) {
        try {
            if (TRUST_ALL_SSL && BASE_EXEC_POST.startsWith("https://")) trustAllSsl();

            List<Map<String, String>> rows = readXlsxAsMaps(EXCEL_FILE);
            System.out.println("Строк к отправке: " + rows.size());
            int i = 0;

            for (Map<String, String> row : rows) {
                i++;
                String json = GSON.toJson(row);           // один JSON-объект на строку
                String body = buildFormBody(json);        // accessKey/func/params='%JSON%' (URL-encoded в теле)

                int code = postFormBody(BASE_EXEC_POST, body);
                System.out.printf("Row %d/%d -> HTTP %d%n", i, rows.size(), code);

                if (i < rows.size()) Thread.sleep(PAUSE_MS);
            }
            System.out.println("Готово.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Excel: первая строка — заголовки; далее — данные (всё читаем как строки) =====
    static List<Map<String, String>> readXlsxAsMaps(String path) throws IOException {
        List<Map<String, String>> out = new ArrayList<>();
        try (InputStream in = new FileInputStream(path);
             Workbook wb = new XSSFWorkbook(in)) {

            Sheet sh = wb.getSheetAt(0);
            if (sh == null) return out;

            Row header = sh.getRow(0);
            if (header == null) return out;

            int cols = header.getLastCellNum();
            String[] names = new String[cols];
            DataFormatter fmt = new DataFormatter(); // как видно в Excel

            for (int c = 0; c < cols; c++) {
                names[c] = fmt.formatCellValue(header.getCell(c)).trim();
                if (names[c].isEmpty()) names[c] = "Column" + (c + 1);
            }

            for (int r = 1; r <= sh.getLastRowNum(); r++) {
                Row row = sh.getRow(r);
                if (row == null) continue;

                Map<String, String> m = new LinkedHashMap<>();
                boolean allEmpty = true;

                for (int c = 0; c < cols; c++) {
                    String v = fmt.formatCellValue(row.getCell(c)).trim();
                    if (!v.isEmpty()) allEmpty = false;
                    m.put(names[c], v);
                }
                if (!allEmpty) out.add(m); // пропускаем полностью пустые строки
            }
        }
        return out;
    }

    // ===== Сборка тела формы: accessKey=...&func=...&params='%JSON%' (всё URL-encoded) =====
    static String buildFormBody(String jsonText) {
        String paramsWrapped = "'" + jsonText + "'"; // модуль ждёт одинарные кавычки вокруг JSON
        return "accessKey=" + urlEnc(ACCESS_KEY)
             + "&func="     + urlEnc(FUNC)
             + "&params="   + urlEnc(paramsWrapped);
    }

    static String urlEnc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ===== POST формы (application/x-www-form-urlencoded) =====
    static int postFormBody(String baseUrl, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.setDoOutput(true);

        try (OutputStream os = c.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = c.getResponseCode();
        try (InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream()) {
            if (is != null) {
                String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (!resp.isBlank()) {
                    System.out.println("Response: " + (resp.length() > 800 ? resp.substring(0, 800) + "..." : resp));
                }
            }
        }
        c.disconnect();
        return code;
    }

    // ===== Аналог curl -k (использовать только если реально https:// и это допустимо политиками) =====
    static void trustAllSsl() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{ new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] xcs, String a) {}
            public void checkServerTrusted(X509Certificate[] xcs, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
    }
}