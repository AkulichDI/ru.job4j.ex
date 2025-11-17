import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class SimpleJsonPoster {

    private static final String BASE_URL =
            "http://HOST:8080/sd/services/rest/exec-post";

    private static final String ACCESS_KEY = "CHANGE_ME_ACCESS_KEY";

    private static final String FUNC_NAME = "modules.customApi.migration";

    private static final String JSON_TEXT = "{\n" +
            "  \"field1\": \"value1\",\n" +
            "  \"field2\": 123,\n" +
            "  \"nested\": {\n" +
            "    \"inner\": true\n" +
            "  }\n" +
            "}";

    public static void main(String[] args) {
        try {
            String url = buildExecPostUrl();
            System.out.println("URL: " + url);
            System.out.println("JSON:");
            System.out.println(JSON_TEXT);

            String response = sendJsonAsParams(url, JSON_TEXT);
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
          .append("&func=").append(URLEncoder.encode(FUNC_NAME, StandardCharsets.UTF_8.toString()));

        return sb.toString();
    }

    private static String sendJsonAsParams(String urlStr, String jsonText) throws IOException {
        String encodedJson = URLEncoder.encode(jsonText, StandardCharsets.UTF_8.toString());
        String formBody = "params=" + encodedJson;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
        );
        conn.setDoOutput(true);

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

        return "HTTP " + code + "\n" + responseBody;
    }
}
```0