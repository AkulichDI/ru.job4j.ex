import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.stream.Collectors;

public class SimpleJsonPoster {

    private static final String BASE_URL =
            "https://HOST:8080/sd/services/rest/exec-post";

    private static final String ACCESS_KEY = "CHANGE_ME_ACCESS_KEY";

    private static final String FUNC_NAME = "modules.customApi.addServCallApi1";

    private static final String JSON_TEXT = "{\n" +
            "  \"servicecall\": \"SC-0001\",\n" +
            "  \"service\": \"Service A\",\n" +
            "  \"infoservisiy\": \"Info text\",\n" +
            "  \"servULag\": \"Lag-01\",\n" +
            "  \"agreement\": \"AGR-001\",\n" +
            "  \"description\": \"Test description\"\n" +
            "}";

    public static void main(String[] args) {
        try {
            trustAllSsl();

            String url = buildExecPostUrl();
            System.out.println("URL: " + url);
            System.out.println("JSON_TEXT:");
            System.out.println(JSON_TEXT);

            String response = sendJsonBody(url, JSON_TEXT);
            System.out.println("Response:");
            System.out.println(response);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildExecPostUrl() throws IOException {
        StringBuilder sb = new StringBuilder(BASE_URL);
        char join = BASE_URL.contains("?") ? '&' : '?';

        sb.append(join)
          .append("accessKey=").append(URLEncoder.encode(ACCESS_KEY, StandardCharsets.UTF_8.toString()))
          .append("&func=").append(URLEncoder.encode(FUNC_NAME, StandardCharsets.UTF_8.toString()))
          .append("&params=").append(URLEncoder.encode("requestContent", StandardCharsets.UTF_8.toString()));

        return sb.toString();
    }

    private static String sendJsonBody(String urlStr, String jsonText) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8"
        );
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] bytes = jsonText.getBytes(StandardCharsets.UTF_8);
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

        return "HTTP " + code + "\n" + responseBody;
    }

    private static void trustAllSsl() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] xcs, String s) {}
                    public void checkServerTrusted(X509Certificate[] xcs, String s) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
    }
}