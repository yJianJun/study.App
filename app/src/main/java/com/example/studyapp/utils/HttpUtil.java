package com.example.studyapp.utils;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

public class HttpUtil {
    private static final String TAG = "HttpUtil";

    public static String requestGet(String url) throws IOException {
        Log.d(TAG, "[requestGet][url=" + url + "]");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        // 配置请求参数
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoInput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);

        // 连接
        connection.connect();

        // 检查响应代码
        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            Log.d(TAG, "[requestGet][responseCode=" + responseCode + "]");
            return readResponseBody(connection);
        } else {
            // 记录错误信息
            InputStream errorStream = connection.getErrorStream();
            String errorResponse = "";
            if (errorStream != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = errorStream.read(buffer)) > 0) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                errorResponse = byteArrayOutputStream.toString();
            }
            connection.disconnect();
            LogFileUtil.logAndWrite(android.util.Log.ERROR, TAG, "HTTP request failed with code " + responseCode + ". Error: " + errorResponse,null);
            throw new IOException("HTTP request failed with code " + responseCode + ". Error: " + errorResponse);
        }
    }



    private static String readResponseBody(HttpURLConnection connection) throws IOException {
        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[0x400];
        while(true) {
            int l = inputStream.read(buffer);
            if(l == -1) {
                break;
            }

            byteArrayOutputStream.write(buffer, 0, l);
        }

        return byteArrayOutputStream.toString();
    }

    public static String fillUrlPlaceholders(String url, Map<String, String> params) {
        // 遍历 Map，将占位符替换成实际值
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}"; // 生成占位符格式
            String value = entry.getValue(); // 要替换的实际值
            url = url.replace(placeholder, value); // 替换
        }
        return url;
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        // 如果不是回环地址且是 IPv4 地址
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            LogFileUtil.logAndWrite(android.util.Log.ERROR, TAG, "Error getting local IP address", e);
        }
        return "0.0.0.0"; // 无法获取时返回默认值
    }


}
