package com.example.studyapp.device;

public class Native {
    static {
        try {
            System.loadLibrary("native");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
            // 添加日志或通知以便诊断问题
        }
    }

    public native static long[] getSysInfo();

    public native static void getPower();

    public native static void enableBypassAntiDebug(int uid);

    public native static void setBootId(String bootIdHexStr);

    public native static void deviceInfoShow();
    public native static void deviceInfoChange();

    public native static void deviceInfoReset();

    public native static boolean cpuInfoChange(String path);
    public native static boolean changeMemSize(String path);
}
