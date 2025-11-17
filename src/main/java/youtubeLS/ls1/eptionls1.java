import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class PostJsonExample {
    public static void main(String[] args) {
        try {
            // Создаём HTTP клиент
            HttpClient client = HttpClient.newHttpClient();

            // JSON с кириллицей
            String json = "{"name":"Иван","city":"Владивосток","message":"Привет мир!"}";

            // Формируем POST-запрос
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://httpbin.org/post"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            // Отправляем запрос и получаем ответ
            HttpResponse<String> response = client.send(request, 
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // Выводим результат
            System.out.println("Статус код: " + response.statusCode());
            System.out.println("Ответ сервера:");
            System.out.println(response.body());

        } catch (Exception e) {
            System.err.println("Ошибка при выполнении запроса: " + e.getMessage());
            e.printStackTrace();
        }
    }
}