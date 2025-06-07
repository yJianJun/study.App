package com.example.studyapp.config;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConfigLoader {

    // 从 assets 中读取 JSON 文件并解析
    public static String getTunnelAddress(Context context) {
        String jsonStr;
        // 获取应用私有目录的文件路径
        File configFile = new File(context.getCodeCacheDir(),"config.json");

        // 检查文件是否存在
        if (!configFile.exists()) {
            return "172.19.0.1"; // 返回默认地址
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(configFile)))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            jsonStr = stringBuilder.toString();

            // 解析 JSON
            JSONObject jsonObject = new JSONObject(jsonStr);
            return jsonObject.optString("tun_address", "172.19.0.1");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return "172.19.0.1";
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return "172.19.0.1";
        }
    }
}
