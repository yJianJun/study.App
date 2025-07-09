package com.example.retention.config;

import android.util.Log;
import com.example.retention.utils.LogFileUtil;

/**
 * @Time: 2025-08-09 21:08
 * @Creator: 初屿贤
 * @File: ewfw
 * @Project: study.App
 * @Description:
 */
public class CountryCode {
    public static final int DEVICE_TYPE = 2;
    static final String US = "US";
    static final String RU = "RU";
    // 默认使用美国
    static final String DEFAULT = US;

    // 当前使用的国家代码
    public static String currentCountry = DEFAULT;

    public static String switchCountry() {
        currentCountry = currentCountry.equals(US) ?
                RU : US;
        LogFileUtil.logAndWrite(Log.INFO, "TAG",
                "Switched country to: " + currentCountry, null);
        return currentCountry;
    }
}
