package com.example.studyapp.utils;

import static com.example.studyapp.utils.ZipUtils.getAllApkFiles;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApkInstaller {
    public static boolean batchInstallWithRoot(String dirPath) {

        // 获取APK文件
        List<File> apkFiles = getAllApkFiles(dirPath);

        return installSplitApks(apkFiles);
    }

    private static boolean isSplitApk(List<File> apkFiles) {
        for (File apk : apkFiles) {
            if (apk.getName().contains("base.apk")) {
                return true;
            }
        }
        return false;
    }

    private static boolean installSplitApks( List<File> apkFiles) {
        // 确保base.apk在第一位
        File baseApk = null;
        List<File> otherApks = new ArrayList<>();

        for (File apk : apkFiles) {
            if (apk.getName().contains("base.apk")) {
                baseApk = apk;
            } else {
                otherApks.add(apk);
            }
        }

        if (baseApk == null) {
            Log.d("TAG", "installSplitApks: 没有 base apk");
            return false;
        }

        // 构建安装命令
        StringBuilder cmd = new StringBuilder("pm install-multiple \"")
                .append(baseApk.getAbsolutePath()).append("\"");

        for (File apk : otherApks) {
            cmd.append(" \"").append(apk.getAbsolutePath()).append("\"");
        }
        Log.d("TAG", "installSplitApks: "+cmd);
        // 执行命令
        String result = ShellUtils.execRootCmdAndGetResult(cmd.toString());
        if (result != null && result.contains("Success")) {
            Log.d("TAG", "installSplitApks: install success");
            return true;
        } else {
            Log.d("TAG", "installSplitApks: install failed");
            return false;
        }
    }

}
