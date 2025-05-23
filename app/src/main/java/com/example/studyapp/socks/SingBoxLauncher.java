package com.example.studyapp.socks;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.studyapp.utils.ShellUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SingBoxLauncher {
    private static SingBoxLauncher instance;
    private static final String PKG = "io.nekohasekai.sfa";

    private final CountDownLatch latch;

    public static SingBoxLauncher getInstance() {
        if (instance == null) {
            synchronized (SingBoxLauncher.class) {
                if (instance == null) {
                    instance = new SingBoxLauncher();
                }
            }
        }
        return instance;
    }

    private final HandlerThread handlerThread;

    private SingBoxLauncher() {
        latch = new CountDownLatch(1);
        handlerThread = new HandlerThread("SingBoxThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    public void shutdown() {
        handlerThread.quitSafely();
    }

    private void checkVPNApp(int count) {
        if (count > 3) {
            Log.e("checkVPNApp", "Invalid count parameter: " + count);
            return;
        }

        while (count <= 3) {
            try {
                if (!isProcessRunning(PKG)) {
                    startApp(PKG);
                } else {
                    break;
                }
                // 避免 Thread.sleep，改用异步任务定时调度
                TimeUnit.SECONDS.sleep(2);
                count++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                Log.e("checkVPNApp", "Thread interrupted: " + e.getMessage());
                break;
            }
        }
    }

    // 工具方法
    private boolean isProcessRunning(String pkg) {
        String command = "ps -A | grep " + sanitizePackageName(pkg);
        String result = ShellUtils.execRootCmdAndGetResult(command);
        return result != null && !result.isEmpty();
    }

    private void startApp(String pkg) {
        ShellUtils.execRootCmd("am start -n " + sanitizePackageName(pkg) + "/.ui.MainActivity");
    }

    private String sanitizePackageName(String pkg) {
        if (pkg.matches("^[a-zA-Z0-9._-]+$")) {
            return pkg;
        } else {
            throw new IllegalArgumentException("Unsafe package name: " + pkg);
        }
    }

    public boolean start(Context context,String originIp) {
        checkVPNApp(0);

        Future<?> future = executorService.submit(() -> {

            String content = "{\n" +
                    "  \"dns\": {\n" +
                    "    \"independent_cache\": true,\n" +
                    "    \"servers\": [\n" +
                    "      {\n" +
                    "        \"tag\": \"cloudflare\",\n" +
                    "        \"address\": \"tls://1.1.1.1\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"tag\": \"local\",\n" +
                    "        \"address\": \"tls://1.1.1.1\",\n" +
                    "        \"detour\": \"direct\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"tag\": \"remote\",\n" +
                    "        \"address\": \"fakeip\"\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"rules\": [\n" +
                    "      {\n" +
                    "        \"server\": \"local\",\n" +
                    "        \"outbound\": \"any\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"server\": \"remote\",\n" +
                    "        \"query_type\": [\n" +
                    "          \"A\",\n" +
                    "          \"AAAA\"\n" +
                    "        ]\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"fakeip\": {\n" +
                    "      \"enabled\": true,\n" +
                    "      \"inet4_range\": \"198.18.0.0/15\",\n" +
                    "      \"inet6_range\": \"fc00::/18\"\n" +
                    "    },\n" +
                    "    \"final\": \"cloudflare\"\n" +
                    "  },\n" +
                    "  \"inbounds\": [\n" +
                    "    {\n" +
                    "      \"type\": \"tun\",\n" +
                    "      \"tag\": \"tun-in\",\n" +
                    "      \"address\": [\n" +
                    "        \"172.19.0.1/28\"\n" +
                    "      ],\n" +
                    "      \"auto_route\": true,\n" +
                    "      \"sniff\": true,\n" +
                    "      \"strict_route\": false,\n" +
                    "      \"domain_strategy\": \"ipv4_only\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"log\": {\n" +
                    "    \"level\": \"trace\"\n" +
                    "  },\n" +
                    "  \"outbounds\": [\n" +
                    "    {\n" +
                    "      \"type\": \"socks\",\n" +
                    "      \"tag\": \"proxy\",\n" +
                    "      \"version\": \"5\",\n" +
                    "      \"network\": \"tcp\",\n" +
                    "      \"udp_over_tcp\": {\n" +
                    "        \"enabled\": true\n" +
                    "      },\n" +
                    "      \"username\": \"cut_team_protoc_vast-zone-custom-region-us\",\n" +
                    "      \"password\": \"Leoliu811001\",\n" +
                    "      \"server\": \"105bd58a50330382.na.ipidea.online\",\n" +
                    "      \"server_port\": 2333\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"type\": \"dns\",\n" +
                    "      \"tag\": \"dns-out\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"type\": \"direct\",\n" +
                    "      \"tag\": \"direct\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"type\": \"block\",\n" +
                    "      \"tag\": \"block\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"route\": {\n" +
                    "    \"final\": \"proxy\",\n" +
                    "    \"auto_detect_interface\": true,\n" +
                    "    \"rules\": [\n" +
                    "      {\n" +
                    "        \"protocol\": \"dns\",\n" +
                    "        \"outbound\": \"dns-out\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"protocol\": [\n" +
                    "          \"stun\",\n" +
                    "          \"quic\"\n" +
                    "        ],\n" +
                    "        \"outbound\": \"block\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"ip_is_private\": true,\n" +
                    "        \"outbound\": \"direct\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"ip_cidr\": \"\",\n" +
                    "        \"outbound\": \"direct\"\n" +
                    "      },\n" +
                    "      {\n" +
                    "        \"domain\": \"cpm-api.resi-prod.resi-oversea.com\",\n" +
                    "        \"domain_suffix\": \"resi-oversea.com\",\n" +
                    "        \"outbound\": \"direct\"\n" +
                    "      }\n" +
                    "    ]\n" +
                    "  }\n" +
                    "}";

            Intent intent = new Intent();
            intent.setAction(PKG + ".action.START_VPN");
            intent.putExtra("content", content);
            intent.putExtra("originIp", originIp);
            context.sendBroadcast(intent);

            handler.postDelayed(() -> checkVPN(context, 0), 1000);
        });

        try {
            future.get(); // 等待任务完成，捕获异常
        } catch (Exception e) {
            Log.e("TaskError", "Task failed to execute properly", e);
        }

        try {
            boolean result = latch.await(5, TimeUnit.SECONDS); // 设置超时时间
            if (!result) {
                isVpnRunning = false; // 超时回退逻辑
            }
        } catch (InterruptedException ignored) {
            isVpnRunning = false;
        }
        return isVpnRunning;
    }

    public void stop(Context context) {
        if (context == null || PKG == null || PKG.trim().isEmpty()) {
            Log.e("SingBoxLauncher", "Invalid context or package name.");
            return;
        }

        try {
            // 构建广播
            Intent intent = new Intent();
            intent.setAction(PKG + ".action.STOP_VPN");
            intent.setPackage(PKG); // 安全性增强，限制接收者
            context.sendBroadcast(intent);

            // 检查进程是否仍在运行
            if (isProcessRunning(PKG)) {
                // 添加延迟等待，确保广播完成
                Thread.sleep(1000);

                // 执行强制停止命令
                ShellUtils.execRootCmd("am force-stop " + sanitizePackageName(PKG));
            }
        } catch (Exception e) {
            Log.e("SingBoxLauncher", "Error while stopping VPN app: " + e.getMessage());
        }
    }

    private void checkVPN(Context context, int count) {
        try {
            if (checkVPN(context)) {
                isVpnRunning = true;
                Log.d("VPNCheck", "VPN is running.");
                latch.countDown();
            } else {
                Log.d("VPNCheck", "VPN is not running. Retry count: " + count);
                if (count > 3) {
                    isVpnRunning = false;
                    latch.countDown();
                } else {
                    final int countFinal = count + 1;
                    handler.removeCallbacksAndMessages(null); // 清理旧任务
                    handler.postDelayed(() -> checkVPN(context, countFinal), 1000);
                }
            }
        } catch (Exception e) {
            Log.e("VPNCheck", "Error in checkVPN", e);
            isVpnRunning = false;
            latch.countDown();
        }
    }
    // 检查网络中的 VPN 连接
    private boolean checkVPN(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;

        android.net.Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;

        android.net.NetworkCapabilities networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        if (networkCapabilities == null) return false;

        return networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
    }

    private boolean isVpnRunning;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler;
}
