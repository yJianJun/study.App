# 保留 ArmCloudApiClient 及其所有公共方法和构造函数
-keep class com.example.retention.device.ArmCloudApiClient {
    public <init>();
    public *;
}

# 保留 PropertyItem 内部类及其字段、构造函数和 toJson 方法
-keep class com.example.retention.device.ArmCloudApiClient$PropertyItem {
    private java.lang.String propertiesName;
    private java.lang.String propertiesValue;
    public <init>(java.lang.String, java.lang.String);
    public org.json.JSONObject toJson();
}

# 保留枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留使用 @SerializedName 注解的字段
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留注解信息（根据需要启用）
# -keepattributes *Annotation*
