package com.example.studyapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 检查存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE_PERMISSION);
        }

        // 查找按钮对象
        Button runScriptButton = findViewById(R.id.run_script_button);
        if (runScriptButton != null) {
            runScriptButton.setOnClickListener(view -> runAutojsScript()); // 设置点击事件
        } else {
            Toast.makeText(this, "Button not found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void runAutojsScript() {
        // 定义脚本文件路径
        File scriptFile = new File(getExternalFilesDir(null), "脚本/adsense.js");

        // 检查文件是否存在
        if (!scriptFile.exists()) {
            Toast.makeText(this, "Script file not found: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查 Auto.js 应用是否安装
        if (!isAppInstalled("org.autojs.autojs6")) {
            Toast.makeText(this, "Auto.js app not installed", Toast.LENGTH_SHORT).show();
            return;
        }

        // 准备启动 Auto.js 的 Intent
        Intent intent = new Intent();
        intent.setClassName("org.autojs.autojs6", "org.autojs.autojs.external.open.RunIntentActivity");
        intent.putExtra("path", scriptFile.getAbsolutePath()); // 传递脚本路径
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 启动 Auto.js
        try {
            startActivity(intent);
            Toast.makeText(this, "Running script: " + scriptFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to run script", Toast.LENGTH_SHORT).show();
        }
    }

    // 检查目标应用是否安装
    private boolean isAppInstalled(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}