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
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        /*connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
        connection.setRequestProperty("X-Requested-With", "com.android.chrome");
        connection.setRequestProperty("Sec-Fetch-Mode", "navigate");
        connection.setRequestProperty("Sec-Fetch-User", "?1");
        connection.setRequestProperty("Sec-Fetch-Dest", "document");
        connection.setRequestProperty("Sec-Fetch-Site", "none");
        connection.setRequestProperty("Sec-Ch-Ua-Mobile", "?1");
        connection.setRequestProperty("Sec-Ch-Ua-Platform", "Android");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
        connection.setInstanceFollowRedirects(true);*/
        connection.setDoInput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setUseCaches(false);
        connection.connect();

        String responseBody = readResponseBody(connection);
        Log.d(TAG, "[requestGet][response=" + responseBody + "]");
        connection.disconnect();

        return responseBody;
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
            e.printStackTrace();
        }
        return "0.0.0.0"; // 无法获取时返回默认值
    }


}
