package com.example.studyapp.device;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import com.example.studyapp.utils.LogFileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @Time: 2025-06-08 17:06
 * @Creator: 初屿贤
 * @File: ArmCloudApiClient
 * @Project: study.App
 * @Description:
 */
public class ArmCloudApiClient {

  private final OkHttpClient client;
  private final String baseUrl;
  private final String accessKey;
  private final String secretKey;

  public ArmCloudApiClient(String baseUrl, String accessKey, String secretKey) {
    this.client = new Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();
    this.baseUrl = baseUrl;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
  }

  private static final String ALGORITHM = "HmacSHA256";

  public static String calculateSignature(String timestamp, String path, String body, String secretKey) throws Exception {
    String stringToSign = timestamp + path + (body != null ? body : "");
    Mac hmacSha256 = Mac.getInstance(ALGORITHM);
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    hmacSha256.init(secretKeySpec);
    byte[] hash = hmacSha256.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(hash);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder hexString = new StringBuilder();
    for (byte b : bytes) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  /**
   * 修改实例安卓改机属性 静态设置安卓改机属性，需要重启实例才能够生效，一般用于修改设备信息 该接口与修改实例属性接口的区别在于生效时机，该接口生效时间为每次开机初始化 设置实例属性后，属性数据会持久化存储，重启或重置实例无需再调用该接口
   *
   * @param padCode 实例 ID，非空
   * @param props   属性映射，非空
   * @param restart 是否自动重启
   * @return 接口返回结果字符串
   * @throws IOException 请求失败或网络错误
   */
  public String updateAndroidModProperties(String padCode, Map<String, String> props, boolean restart) throws IOException {
    // 参数校验
    if (padCode == null || padCode.isEmpty()) {
      throw new IllegalArgumentException("padCode 不能为空");
    }
    if (props == null) {
      throw new IllegalArgumentException("props 不能为 null");
    }

    // 构造请求体
    JSONObject json = new JSONObject();

    try {
      json.put("padCode", padCode);
      json.put("props", new JSONObject(props));
      json.put("restart", restart);
    } catch (JSONException e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateAndroidModProperties: JSON error", e);
    }

    String jsonBody = json.toString();

    // 生成时间戳
    String timestamp = String.valueOf(System.currentTimeMillis());

    String API_PATH = "/openapi/open/pad/updatePadAndroidProp";

    String signature = "";
    try {
      signature = calculateSignature(timestamp, API_PATH, jsonBody, secretKey);
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateAndroidModProperties: Signature error", e);
    }

    RequestBody body = RequestBody.create(
        MediaType.parse("application/json; charset=utf-8"),
        jsonBody
    );

    Request request = new Request.Builder()
        .url(baseUrl + API_PATH)
        .addHeader("authver", "2.0")
        .addHeader("x-ak", accessKey)
        .addHeader("x-timestamp", timestamp)
        .addHeader("x-sign", signature)
        .post(body)
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("请求失败: " + response);
      }

      ResponseBody responseBody = response.body();
      if (responseBody == null) {
        throw new IOException("响应体为空");
      }

      return responseBody.string();
    }
  }


  /**
   * 修改实例属性 动态修改实例的属性信息，包括系统属性和设置 实例需要处于开机状态，该接口为即时生效
   * <p>
   * 示例： String[] padCodes = new String[]{"AC21020010001"}; List<PropertyItem> systemProps = Arrays.asList( new PropertyItem("ro.build.id", "QQ3A.200805.001")
   * ); List<PropertyItem> oaidProps = Arrays.asList( new PropertyItem("oaid", "001") );
   * <p>
   * String response = client.updateInstanceProperties( padCodes, null,  // modemPersistProps null,  // modemProps null,  // systemPersistProps systemProps,
   * null,  // settingProps oaidProps );
   */
  public String updateInstanceProperties(
      String[] padCodes,
      List<PropertyItem> modemPersistProps,
      List<PropertyItem> modemProps,
      List<PropertyItem> systemPersistProps,
      List<PropertyItem> systemProps,
      List<PropertyItem> settingProps,
      List<PropertyItem> oaidProps
  ) throws IOException {
    if (padCodes == null || padCodes.length == 0) {
      throw new IllegalArgumentException("padCodes 不能为空");
    }

    JSONObject json = new JSONObject();
    try {
      json.put("padCodes", new JSONArray(padCodes));
      putPropertyItems(json, "modemPersistPropertiesList", modemPersistProps);
      putPropertyItems(json, "modemPropertiesList", modemProps);
      putPropertyItems(json, "systemPersistPropertiesList", systemPersistProps);
      putPropertyItems(json, "systemPropertiesList", systemProps);
      putPropertyItems(json, "settingPropertiesList", settingProps);
      putPropertyItems(json, "oaidPropertiesList", oaidProps);

    } catch (JSONException e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateInstanceProperties: JSON error", e);
    }

    String jsonBody = json.toString();
    String timestamp = String.valueOf(System.currentTimeMillis());
    String API_PATH = "/openapi/open/pad/updatePadProperties";

    if (secretKey == null || secretKey.isEmpty()) {
      throw new IllegalArgumentException("secretKey 不能为空");
    }

    String signature = null;
    try {
      signature = calculateSignature(timestamp, API_PATH, jsonBody, secretKey);
    } catch (Exception e) {
      LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateInstanceProperties: calculateSignature error", e);
    }

    RequestBody body = RequestBody.create(
        MediaType.parse("application/json; charset=utf-8"),
        jsonBody
    );

    Request request = new Request.Builder()
        .url(baseUrl + API_PATH)
        .addHeader("authver", "2.0")
        .addHeader("x-ak", accessKey)
        .addHeader("x-timestamp", timestamp)
        .addHeader("x-sign", signature)
        .post(body)
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("请求失败: " + response);
      }

      try (ResponseBody responseBody = response.body()) {
        return responseBody != null ? responseBody.string() : "";
      }
    }
  }

  private void putPropertyItems(JSONObject json, String key, List<PropertyItem> items) throws JSONException {
    if (items != null && !items.isEmpty()) {
      JSONArray array = new JSONArray();
      for (PropertyItem item : items) {
        array.put(item.toJson());
      }
      json.put(key, array);
    }
  }


  public static class PropertyItem {

    private String propertiesName;
    private String propertiesValue;

    public PropertyItem(String name, String value) {
      this.propertiesName = name;
      this.propertiesValue = value;
    }

    public JSONObject toJson() throws JSONException {
      JSONObject json = new JSONObject();
      json.put("propertiesName", propertiesName);
      json.put("propertiesValue", propertiesValue);
      return json;
    }
  }
}


