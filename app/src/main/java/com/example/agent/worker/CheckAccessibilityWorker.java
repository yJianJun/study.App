package com.example.agent.worker;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.work.CoroutineWorker;
import androidx.work.WorkerParameters;
import com.example.agent.service.MyAccessibilityService;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CheckAccessibilityWorker extends CoroutineWorker {

    public CheckAccessibilityWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public boolean isAccessibilityServiceEnabled(Context context, Class<? extends AccessibilityService> service) {
        String enabledServices = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        // 检查是否获取到了内容并进行处理
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        // 利用 split 高效解析字符串
        String[] components = enabledServices.split(":");
        String expectedComponentName = new ComponentName(context.getPackageName(), service.getCanonicalName()).flattenToString();

        // 使用 foreach 检查是否匹配
        for (String componentName : components) {
            if (expectedComponentName.equalsIgnoreCase(componentName)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public @Nullable Object doWork(@NotNull Continuation<? super Result> continuation) {
        if (!isAccessibilityServiceEnabled(getApplicationContext(), MyAccessibilityService.class)) {
            // 判断是否已经提示过用户引导开启
            SharedPreferences sharedPreferences = getApplicationContext()
                .getSharedPreferences("my_app_prefs", Context.MODE_PRIVATE);
            boolean hasPrompted = sharedPreferences.getBoolean("accessibility_prompted", false);

            if (!hasPrompted) {
                // 引导用户打开辅助功能服务
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getApplicationContext().startActivity(intent);

                // 更新状态
                sharedPreferences.edit().putBoolean("accessibility_prompted", true).apply();
            }
            return Result.retry();
        }
        return Result.success();
    }
}
