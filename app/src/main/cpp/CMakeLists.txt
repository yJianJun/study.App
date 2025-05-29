   cmake_minimum_required(VERSION 3.10.2) # 指定最低版本
   project("native_lib") # 定义项目名称

   # 设置源文件列表
   set(SOURCE_FILES
          infoct.cpp # 确保路径正确
   )
   set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)
   set(CMAKE_BUILD_TYPE Debug) # 确保构建为 Debug 模式
   set(CMAKE_NO_STRIP ON)      # 禁止符号剥离
   set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})


   # 包含头文件目录
   include_directories(${CMAKE_SOURCE_DIR})# 确保 fk_common_struct.h 可用

   # 生成共享库
   add_library( # 设置生成的库名称
           native
           SHARED
           ${SOURCE_FILES}
   )

   # 链接系统库（例如 log 日志和 libc 等）
   target_link_libraries(
           native
           log   # 用于 __android_log_print
   )