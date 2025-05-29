//

#define CMD_BASE 0xCBAABC10

#define FK_ADD_UID (CMD_BASE + 1)
#define FK_REMOVE_UID (CMD_BASE + 2)
#define FK_REMOVE_ALL_UID (CMD_BASE + 3)
#define FK_SET_ANTI_DEBUG_STATUS (CMD_BASE + 4)

#define FK_GET_POWER (CMD_BASE + 10)
#define FK_GET_POWER2 (CMD_BASE + 12)
#define FK_RELEASE_POWER2 (CMD_BASE + 13)


#define FK_HAS_POWER (CMD_BASE + 20)

#define FK_SET_CURRENT_NS_BOOT_ID (CMD_BASE + 30)
#define FK_SET_CURRENT_UNAME (CMD_BASE + 31)

#define FK_SET_CURRENT_RAMSIZE  (CMD_BASE + 51)
#define FK_SET_CURRENT_SYSTEM_UPTIME_OFFSET  (CMD_BASE + 52)
#define FK_SET_CURRENT_DISK_INFO  (CMD_BASE + 53)


#define FK_RESET_IO_REDIRECT (CMD_BASE + 100)
#define FK_ADD_REDIRECT_ITEM (CMD_BASE + 101)
#define FK_REMOVE_REDIRECT_ITEM (CMD_BASE + 102)

#define FK_RESET_FILE_PATH_MOCK (CMD_BASE + 110)
#define FK_ADD_FILE_PATH_MOCK (CMD_BASE + 111)

#define FK_CMD_MAX_VAL (CMD_BASE + 10000)

#define FK_OP_SUCCESS 0
#define FK_OP_FAILED 1000

#define ARRAY_LENGTH(x) (sizeof(x) / sizeof(x[0]))

//
#define FILE_PATH_MOCK_INFO_MAX 32
struct file_path_mock_info {
    char filepath[256];
    int status; // 0
    long atime;
    long mtime;
    long ctime;
    long long rom_size;
    long long free_size;
    long long avail_size;
};

#define FK_DISK_INFO_MAX 8
struct fk_disk_info {
    long long rom_size;
    long long free_size;
    long long avail_size;
};

#define BLACK_UID_MAX 32


#define REDIRECT_ITEM_MAX 32
struct io_redirect_item {
    int status; // 0 for not used, 1 for used
    char key[PATH_MAX];
    char value[PATH_MAX];
};