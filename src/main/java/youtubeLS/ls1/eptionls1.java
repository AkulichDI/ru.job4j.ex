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





import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Отправляет одну строку (row) на exec-post: accessKey/func/params в ТЕЛЕ POST.
 *  params = '%JSON%' (JSON не трогаем, только кодируем). Ничего не вырезаем из текста. */
public static int sendRowFormNoStrip(String baseExecPost,
                                     String accessKey,
                                     String func,
                                     Map<String,String> row) throws IOException {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create(); // корректный JSON без лишнего HTML-эскейпа
    String json     = gson.toJson(row);           // ваш JSON как есть
    String wrapped  = "'" + json + "'";           // модулю нужно params='%json_text%'
    String body = "accessKey=" + URLEncoder.encode(accessKey, StandardCharsets.UTF_8)
                + "&func="     + URLEncoder.encode(func,       StandardCharsets.UTF_8)
                + "&params="   + URLEncoder.encode(wrapped,    StandardCharsets.UTF_8);

    HttpURLConnection c = (HttpURLConnection) new URL(baseExecPost).openConnection();
    c.setRequestMethod("POST");
    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
    c.setConnectTimeout(15000);
    c.setReadTimeout(60000);
    c.setDoOutput(true);
    try (OutputStream os = c.getOutputStream()) {
        os.write(body.getBytes(StandardCharsets.UTF_8));
    }
    int code = c.getResponseCode();
    try (InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream()) {
        if (is != null) {
            String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (!resp.isBlank()) System.out.println("Naumen response: " +
                    (resp.length() > 800 ? resp.substring(0,800) + "..." : resp));
        }
    }
    c.disconnect();
    return code;
}