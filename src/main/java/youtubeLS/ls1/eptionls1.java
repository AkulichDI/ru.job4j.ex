package youtubeLS.ls1;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class eptionls1 {
    public void readFile (){
        File file = new File("tst.txt");
        Scanner input = null;
        try {
            input = new Scanner(file);
        } catch (FileNotFoundException e) {
            System.out.println("Файл не найден");
        }finally {
            input.close();
        }
    }
    public static void main(String[] args) {




    }




}




import com.google.gson.Gson;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.HttpsURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

public class ExcelRowPoster {

    // ==
    static String EXCEL_FILE     = "C:/data/equipment.xlsx";

    
    static String BASE_EXEC_POST = "http://rest/exec-post";
    static String ACCESS_KEY     = "PASTE_YOUR_ACCESS_KEY";
    static String FUNC           = "modules.apiMigrationITop.migration";

    // Пауза между запросами, чтобы не грузить сервер (подберите под себя)
    static long   PAUSE_MS       = 500;

    // Лимит длины URL (на случай длинных строк); если не влазит — будет предупреждение
    static int    URL_LIMIT      = 6000;

    // Включайте только если реально https:// и нужен аналог curl -k
    static boolean TRUST_ALL_SSL = false;

    static final Gson GSON = new Gson();

    public static void main(String[] args) {
        try {
            if (TRUST_ALL_SSL && BASE_EXEC_POST.startsWith("https://")) trustAllSsl();

            List<Map<String, String>> rows = readXlsxAsMaps(EXCEL_FILE);
            System.out.println("Строк к отправке: " + rows.size());
            int i = 0;
            for (Map<String, String> row : rows) {
                i++;
                String json = GSON.toJson(row);             // ОДИН объект на одну строку
                String url  = buildExecPostUrl(json);       // ...&params='%JSON%'

                if (url.length() > URL_LIMIT) {
                    System.err.printf("WARN: URL длиной %d > %d для строки %d. " +
                                      "Сократите поля/значения или переходите на JSON в теле POST.%n",
                                      url.length(), URL_LIMIT, i);
                }

                int code = postEmptyBody(url);              // Тело пустое; всё в query, как в вашем curl
                System.out.printf("Row %d/%d -> HTTP %d%n", i, rows.size(), code);

                if (i < rows.size()) Thread.sleep(PAUSE_MS);
            }
            System.out.println("Готово.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Excel: первая строка — ключи, дальше — данные; всё читаем как строки =====
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

    // ===== Формируем URL под exec-post: accessKey, func и params='%JSON%' =====
    static String buildExecPostUrl(String jsonText) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder(BASE_EXEC_POST);
        char join = BASE_EXEC_POST.contains("?") ? '&' : '?';
        sb.append(join).append("accessKey=").append(encode(ACCESS_KEY));
        sb.append("&func=").append(encode(FUNC));

        // модуль ждёт params='%json_text%' — одинарные кавычки включаем в значение
        String paramsValue = "'" + jsonText + "'";
        sb.append("&params=").append(encode(paramsValue));
        return sb.toString();
    }

    static String encode(String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
    }

    // ===== POST с пустым телом (как curl -X POST без --data), заголовок как в curl =====
    static int postEmptyBody(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        // zero-length body, но это именно POST
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) { /* ничего не пишем */ }

        int code = c.getResponseCode();
        try (InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream()) {
            if (is != null) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) System.out.println("Response: " + (body.length() > 800 ? body.substring(0, 800) + "..." : body));
            }
        }
        c.disconnect();
        return code;
    }

    // ===== Аналог curl -k (только если реально https://) =====
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