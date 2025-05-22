package com.example.studyapp.utils;

public class DeviceUtils {
    // 加载本地库
    static {
        try {
            System.loadLibrary("native");
        } catch (UnsatisfiedLinkError e) {
            e.printStackTrace();
        }
    }

    /** 设置设备ID */
    public static native void setDeviceId(String deviceId);

    /** 更新Boot ID */
    public static native void updateBootId(String newBootId);

    /** 获取系统信息 */
    public static native long[] getSysInfo();

    /** 修改CPU信息，需系统权限 */
    public static native boolean cpuInfoChange(String path);

    /** 修改内存大小，需系统权限 */
    public static native boolean changeMemSize(String path);
}