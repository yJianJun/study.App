package com.example.studyapp.utils;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
}
