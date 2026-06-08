package site.base;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BaseGroovy {

    public static void main(String[] args) {

        String urlStr = "http://10.106.25.29:8001/javacrawler/converter/jsonToGroovy";

        String requestFile = "request.json";

        String outputFile = "response.txt";

        HttpURLConnection connection = null;

        try {

            // 读取请求体
            byte[] requestBody = Files.readAllBytes(Paths.get(requestFile));

            URL url = new URL(urlStr);

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");

            // 超时
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // 允许输出
            connection.setDoOutput(true);

            // ===== 完全模拟浏览器 =====

            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");

            connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
            );

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Origin",
                    "http://10.106.25.29:8001"
            );

            connection.setRequestProperty(
                    "Referer",
                    "http://10.106.25.29:8001/javacrawler/converter/gui.html"
            );

            // 很重要
            connection.setFixedLengthStreamingMode(requestBody.length);

            // 写入body
            try (OutputStream os = connection.getOutputStream()) {
                os.write(requestBody);
                os.flush();
            }

            // 响应码
            int responseCode = connection.getResponseCode();

            System.out.println("响应码: " + responseCode);

            System.out.println("响应类型: " + connection.getContentType());

            InputStream inputStream;

            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            // 保存响应
            Files.copy(
                    inputStream,
                    Paths.get(outputFile),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println("响应已保存: " + outputFile);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}