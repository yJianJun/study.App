package com.example.studyapp.utils;

import android.text.TextUtils;

import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class IpUtil {
    public static boolean isValidIPAddress(String ipAddress) {
        if ((ipAddress != null) && (!ipAddress.isEmpty()))
        {
            return Pattern.matches("^([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}$", ipAddress);
        }
        return false;
    }
    public static String getClientIp(String url) {
        try {
            String s = HttpUtil.requestGet(url);
            if (!TextUtils.isEmpty(s) && isValidIPAddress(s))
            {
                return s;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
    public static String getClientIp() {
        return getClientIp("http://47.236.153.142/");
    }

    public static String safeClientIp() {
        String result = getClientIp("http://47.236.153.142/");//SG
        if (TextUtils.isEmpty(result))
        {
            result = getClientIp("http://8.211.204.20/");//UK
        }
        return result;
    }

    public static String checkClientIp(String excludeCountry)
    {
        try {
            String s = HttpUtil.requestGet("https://get.geojs.io/v1/ip/country.json");
            if (!TextUtils.isEmpty(s))
            {
                JSONObject json = new JSONObject(s);
                if (excludeCountry.equalsIgnoreCase(json.getString("country")))
                {
                    return "";
                }
                return json.getString("ip");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";

    }

    public static CompletableFuture<String> getClientIpFuture()
    {
        return CompletableFuture.supplyAsync(IpUtil::safeClientIp);
    }
}
