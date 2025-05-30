#include <jni.h>
#include <string>
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>

#include <sys/sysinfo.h>
#include <sys/prctl.h>
#include <jni.h>
#include <linux/utsname.h>
#include <unistd.h>
#include <sys/vfs.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "fk_common_struct.h"

#define MY_LOG_TAG "DeviceTool-native"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MY_LOG_TAG,__VA_ARGS__) // 定义LOGE类型


extern "C"
JNIEXPORT jlongArray

JNICALL
Java_com_example_studyapp_device_Native_getSysInfo(JNIEnv *env, jclass clazz) {
    struct sysinfo s_info;
    sysinfo(&s_info);

    jlongArray ret = env->NewLongArray(6);
    jlong sysInfoArr[6] = {0};

    sysInfoArr[0] = s_info.totalram * s_info.mem_unit;
    sysInfoArr[1] = s_info.freeram * s_info.mem_unit;
    sysInfoArr[2] = s_info.sharedram * s_info.mem_unit;
    sysInfoArr[3] = s_info.bufferram * s_info.mem_unit;
    sysInfoArr[4] = s_info.totalswap * s_info.mem_unit;
    sysInfoArr[5] = s_info.freeswap * s_info.mem_unit;

    env->SetLongArrayRegion(ret, 0, 6, (jlong *) sysInfoArr);

    return ret;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyapp_device_Native_getPower(JNIEnv
*env,
jclass clazz
) {
prctl(FK_GET_POWER, 0, 0, 0);
}


static unsigned char hexCharToByte(char high, char low) {
    unsigned char highValue, lowValue;

    if (isdigit(high)) {
        highValue = high - '0';
    } else {
        highValue = toupper(high) - 'A' + 10;
    }

    if (isdigit(low)) {
        lowValue = low - '0';
    } else {
        lowValue = toupper(low) - 'A' + 10;
    }

    return (highValue << 4) | lowValue;
}

// 函数实现
static size_t hexStrToByteArray(const char *hexStr, unsigned char *byteArray) {
    size_t len = strlen(hexStr);

    if (len % 2 != 0) {
        return 0; // 输入长度无效
    }

    size_t byteArraySize = len / 2;

    for (size_t i = 0; i < byteArraySize; i++) {
        byteArray[i] = hexCharToByte(hexStr[2 * i], hexStr[2 * i + 1]);
    }

    return byteArraySize;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyapp_device_Native_setBootId(JNIEnv
*env,
jclass clazz, jstring
boot_id_hex_str) {
if (boot_id_hex_str == NULL) {
env->
ThrowNew(env
->FindClass("java/lang/IllegalArgumentException"), "boot_id_hex_str is NULL");
return;
}

const char *c_boot_id_hex_str = env->GetStringUTFChars(boot_id_hex_str, 0);
if (c_boot_id_hex_str == NULL) {
env->
ThrowNew(env
->FindClass("java/lang/OutOfMemoryError"), "Unable to allocate memory for string.");
return;
}

size_t length = strlen(c_boot_id_hex_str);
if (length != 32) {
env->
ReleaseStringUTFChars(boot_id_hex_str, c_boot_id_hex_str
);
env->
ThrowNew(env
->FindClass("java/lang/IllegalArgumentException"), "Invalid boot_id length, expected 32.");
return;
}

unsigned char boot_id[16] = {0};
if (!
hexStrToByteArray(c_boot_id_hex_str, boot_id
)) {
env->
ReleaseStringUTFChars(boot_id_hex_str, c_boot_id_hex_str
);
env->
ThrowNew(env
->FindClass("java/lang/IllegalArgumentException"), "hexStrToByteArray failed.");
return;
}

if (prctl(FK_SET_CURRENT_NS_BOOT_ID, boot_id, 0, 0) != 0) {
env->
ReleaseStringUTFChars(boot_id_hex_str, c_boot_id_hex_str
);
env->
ThrowNew(env
->FindClass("java/lang/RuntimeException"), "prctl call failed.");
return;
}

env->
ReleaseStringUTFChars(boot_id_hex_str, c_boot_id_hex_str
);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyapp_device_Native_deviceInfoChange(JNIEnv
*env,
jclass clazz
) {


prctl(FK_RESET_IO_REDIRECT);
prctl(FK_RESET_FILE_PATH_MOCK);
prctl(FK_REMOVE_ALL_UID);

prctl(FK_ADD_UID,

getuid()

);
prctl(FK_ADD_UID, 2000);

struct new_utsname my_utsname = {
        .sysname = "Linux",
        .nodename = "localhost",
        .release = "5.9.0-42",
        .version = "#46-Ubuntu SMP Fri Jul 10 00:24:02 UTC 2020",
        .machine = "aarch64",
        .domainname = "(none)"
};

struct sysinfo s_info;
sysinfo(&s_info);
//4179690496
// 990355536
prctl(FK_SET_CURRENT_UNAME, my_utsname);

long long my_ramsize = 4179690496l * s_info.mem_unit;
prctl(FK_SET_CURRENT_RAMSIZE, &my_ramsize);
prctl(FK_SET_CURRENT_SYSTEM_UPTIME_OFFSET, 1000);


struct fk_disk_info tmp_disk_info = {
        .avail_size = 110735782912,
        .free_size = 100752560128,
        .rom_size = 130041423872l
};
// 修改磁盘大小
// statfs "/data"
// statfs "/sdcard"

prctl(FK_SET_CURRENT_DISK_INFO, tmp_disk_info);


struct file_path_mock_info tmp_file_path_mock_info = {
        .filepath = "/proc/cpuinfo",
        .atime = 0,
        .ctime = 0,
        .mtime = 0
};

// 修改文件时间
int tmpret = prctl(FK_ADD_FILE_PATH_MOCK, tmp_file_path_mock_info);
LOGE("FK_ADD_FILE_PATH_MOCK tmpret : %d", tmpret);

// 路径重定向
tmpret = prctl(FK_ADD_REDIRECT_ITEM, "/proc/cpuinfo", "/proc/meminfo");
LOGE("FK_ADD_REDIRECT_ITEM tmpret : %d", tmpret);

}

extern "C"
JNIEXPORT jboolean
JNICALL
Java_com_example_studyapp_device_Native_cpuInfoChange(JNIEnv * env, jclass, jstring
redirectPath) {
prctl(FK_RESET_IO_REDIRECT);
prctl(FK_RESET_FILE_PATH_MOCK);
prctl(FK_REMOVE_ALL_UID);

prctl(FK_ADD_UID, 2000);

struct file_path_mock_info tmp_file_path_mock_info = {
        .filepath = "/proc/cpuinfo",
        .atime = 0,
        .ctime = 0,
        .mtime = 0
};
const char *newPath = env->GetStringUTFChars(redirectPath, nullptr);
LOGE("cpuinfo(newPath) : %s", newPath);

int ret = prctl(FK_ADD_FILE_PATH_MOCK, tmp_file_path_mock_info);
if (ret == -1)
return false;

ret = prctl(FK_ADD_REDIRECT_ITEM, "/proc/cpuinfo", newPath);

return ret != -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyapp_device_Native_deviceInfoShow(JNIEnv
*env,
jclass clazz
) {
struct sysinfo s_info;
sysinfo(&s_info);

LOGE("s_info.totalram %ld", s_info.totalram);
LOGE("s_info.freeram %ld", s_info.freeram);
LOGE("s_info.sharedram %ld", s_info.sharedram);
LOGE("s_info.bufferram %ld", s_info.bufferram);
LOGE("s_info.mem_unit %ld", s_info.mem_unit);
LOGE("s_info.uptime %ld", s_info.uptime);

struct statfs my_data;
statfs("/data", &my_data);

LOGE("/data rom_size %ld", my_data.f_blocks * my_data.f_bsize);
LOGE("/data free_size %ld", my_data.f_bfree * my_data.f_bsize);
LOGE("/data avail_size %ld", my_data.f_bavail * my_data.f_bsize);


struct stat64 mystat;
int stat_ret = stat64("/proc/cpuinfo", &mystat);
LOGE("stat64 /proc/cpuinfo %d", stat_ret);
LOGE("/proc/cpuinfo st_atim %ld", mystat.st_atim.tv_sec);
LOGE("/proc/cpuinfo st_mtim %ld", mystat.st_mtim.tv_sec);
LOGE("/proc/cpuinfo st_ctim %ld", mystat.st_ctim.tv_sec);


}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyapp_device_Native_deviceInfoReset(JNIEnv
*env,
jclass clazz
) {
prctl(FK_RESET_IO_REDIRECT);
prctl(FK_RESET_FILE_PATH_MOCK);
prctl(FK_REMOVE_ALL_UID);

prctl(FK_SET_CURRENT_UNAME, 0);
prctl(FK_SET_CURRENT_RAMSIZE, 0);
prctl(FK_SET_CURRENT_SYSTEM_UPTIME_OFFSET, 0);
prctl(FK_SET_CURRENT_DISK_INFO, NULL);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_example_studyapp_device_Native_enableBypassAntiDebug(JNIEnv
*env,
jclass clazz, jint
uid) {
prctl(FK_REMOVE_ALL_UID);
prctl(FK_ADD_UID, uid);
prctl(FK_SET_ANTI_DEBUG_STATUS, 1);
}