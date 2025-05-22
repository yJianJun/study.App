package com.example.studyapp;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

public class ProxyVpnService extends VpnService {
    private ParcelFileDescriptor vpnInterface;

    @Override
    public void onCreate() {
        super.onCreate();
        Builder builder = new Builder();
        try {
            builder.addAddress("10.0.2.15", 24); // 配置虛擬 IP
            builder.addRoute("0.0.0.0", 0);      // 配置攔截所有流量的路由
            builder.setSession("Proxy VPN Service");
            builder.addDnsServer("8.8.8.8");    // 設置 DNS
            vpnInterface = builder.establish(); // 啟動 VPN 通道，保存接口描述符
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 在实际实现中可能需要处理 Intent 数据
        return START_STICKY;
    }
}
