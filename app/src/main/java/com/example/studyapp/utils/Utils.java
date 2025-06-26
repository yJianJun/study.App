package com.example.studyapp.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Utils {

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }
        return false;
    }

    public static boolean isAppInstalled(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(0);

        for (ApplicationInfo packageInfo : packages) {
            Log.d("TAG", "isPackageInstalled: "+packageInfo.packageName);
            if (packageInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static void writePackageName(String packageName){
        File file = new File(Environment.getExternalStorageDirectory(),
                "script/packagesname.txt");
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean dirsCreated = parentDir.mkdirs();
            if (!dirsCreated) {
                Log.e("FileWrite", "Failed to create directories: " + parentDir);
                return;
            }
        }
        Log.d("TAG", "writePackageName: "+packageName);
        try (BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(file))) {
            bos.write(packageName.getBytes(StandardCharsets.UTF_8));
            bos.flush(); // 确保数据写入磁盘
        } catch (IOException e) {
            Log.e("FileWrite", "Failed to write package name: " + packageName, e);
            // 6. 可以考虑添加重试机制或通知用户
        }
    }
}
