package com.example.agent.utils;

import android.text.TextUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;

public class IpUtil {
    public static boolean isValidIPAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) return false;
        ipAddress = ipAddress.trim();  // 去除多余字符
        return ipAddress.matches(
                "^(25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})(\\.(25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})){3}$"
        );
    }
    public static String getClientIp(String url) {
        try {
            String s = HttpUtil.requestGet(url);
            if (!TextUtils.isEmpty(s))
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
        return getClientIp("https://get.geojs.io/v1/ip");//SG
    }

    public static boolean isValidIpAddress(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
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
