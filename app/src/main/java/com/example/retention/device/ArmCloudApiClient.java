package com.example.retention.device;

import android.util.Log;
import com.example.retention.utils.LogFileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
  private final String baseUrl = "https://openapi-hk.armcloud.net";
  private final String accessKey;
  private final String secretKey;

  public ArmCloudApiClient(String baseUrl, String accessKey, String secretKey) {
    this.client = new Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build();
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

      // 检查 padCodes 是否有重复项
      Set<String> padCodeSet = new HashSet<>();
      for (String code : padCodes) {
          if (!padCodeSet.add(code)) {
              throw new IllegalArgumentException("padCodes 包含重复项: " + code);
          }
      }

      JSONObject json = new JSONObject();
      try {
          json.put("padCodes", new JSONArray(Arrays.asList(padCodes)));
          putPropertyItems(json, "modemPersistPropertiesList", modemPersistProps);
          putPropertyItems(json, "modemPropertiesList", modemProps);
          putPropertyItems(json, "systemPersistPropertiesList", systemPersistProps);
          putPropertyItems(json, "systemPropertiesList", systemProps);
          putPropertyItems(json, "settingPropertiesList", settingProps);
          putPropertyItems(json, "oaidPropertiesList", oaidProps);

      } catch (JSONException e) {
          LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateInstanceProperties: JSON 构建失败", e);
          throw new IOException("JSON 构建失败", e);
      }

      String jsonBody = json.toString();
      String timestamp = String.valueOf(System.currentTimeMillis());
      String API_PATH = "/openapi/open/pad/updatePadProperties";

      if (secretKey == null || secretKey.isEmpty()) {
          throw new IllegalArgumentException("secretKey 不能为空");
      }

      String signature;
      try {
          signature = calculateSignature(timestamp, API_PATH, jsonBody, secretKey);
      } catch (Exception e) {
          LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateInstanceProperties: 签名计算失败", e);
          throw new IOException("签名计算失败", e);
      }

      RequestBody body = RequestBody.create(
              MediaType.get("application/json; charset=utf-8"),
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

          String responseBodyString = responseBody.string();
          JSONObject responseJson = new JSONObject(responseBodyString);

          // 校验返回码
          if (responseJson.has("code")) {
              int code = responseJson.getInt("code");
              if (code != 200) {
                  String errorMsg = responseJson.optString("msg", "未知错误");
                  LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateInstanceProperties: 接口返回错误码 " + code + ", 错误信息: " + errorMsg, null);
                  throw new IOException("接口返回错误码: " + code + ", 错误信息: " + errorMsg);
              }
          } else {
              LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateInstanceProperties: 响应中缺少 'code' 字段",null);
              throw new IOException("响应中缺少 'code' 字段");
          }

          return responseBodyString;
      } catch (JSONException e) {
          LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "updateInstanceProperties: 响应解析失败", e);
          throw new IOException("响应解析失败", e);
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

  /**
   * 分页查询 ARM 设备列表，并提取 deviceCode
   *
   * @param page                  页码
   * @param rows                  每页条数
   * @param padAllocationStatus   实例分配状态：-2删除失败 -1分配失败 0-未分配；1-分配中 2-已分配 3-删除中
   * @param deviceStatus          物理机状态：0-离线；1-在线
   * @param armServerCode         服务器编码
   * @param deviceCode            物理机编号
   * @param deviceIp              物理机IP
   * @param armServerStatus       服务器状态：0-离线；1-在线
   * @param idc                   机房ID
   * @param deviceIpList          板卡IP列表
   * @return                      匹配的 deviceCode 数组
   * @throws IOException          请求失败或网络错误
   */
  public String[] getDeviceCodes(
          int page,
          int rows,
          Integer padAllocationStatus,
          Integer deviceStatus,
          String armServerCode,
          String deviceCode,
          String deviceIp,
          Integer armServerStatus,
          String idc,
          String[] deviceIpList) throws IOException {

      JSONObject json = new JSONObject();
      try {
          json.put("page", page);
          json.put("rows", rows);

          if (padAllocationStatus != null) json.put("padAllocationStatus", padAllocationStatus);
          if (deviceStatus != null) json.put("deviceStatus", deviceStatus);
          if (armServerCode != null) json.put("armServerCode", armServerCode);
          if (deviceCode != null) json.put("deviceCode", deviceCode);
          if (deviceIp != null) json.put("deviceIp", deviceIp);
          if (armServerStatus != null) json.put("armServerStatus", armServerStatus);
          if (idc != null) json.put("idc", idc);
          if (deviceIpList != null && deviceIpList.length > 0) {
              json.put("deviceIpList", new JSONArray(Arrays.asList(deviceIpList)));
          }

      } catch (JSONException e) {
          LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "getDeviceCodes: JSON 构建失败", e);
          throw new IOException("JSON 构建失败", e);
      }

      String jsonBody = json.toString();
      String timestamp = String.valueOf(System.currentTimeMillis());
      String API_PATH = "/openapi/open/device/list";

      if (secretKey == null || secretKey.isEmpty()) {
          throw new IllegalArgumentException("secretKey 不能为空");
      }

      String signature;
      try {
          signature = calculateSignature(timestamp, API_PATH, jsonBody, secretKey);
      } catch (Exception e) {
          LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "getDeviceCodes: 签名计算失败", e);
          throw new IOException("签名计算失败", e);
      }

      RequestBody body = RequestBody.create(
              MediaType.get("application/json; charset=utf-8"),
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

          String responseBodyString = responseBody.string();
          JSONObject responseJson = new JSONObject(responseBodyString);

          // 校验返回码
          if (responseJson.has("code")) {
              int code = responseJson.getInt("code");
              if (code != 200) {
                  String errorMsg = responseJson.optString("msg", "未知错误");
                  LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "getDeviceCodes: 接口返回错误码 " + code + ", 错误信息: " + errorMsg,null);
                  throw new IOException("接口返回错误码: " + code + ", 错误信息: " + errorMsg);
              }
          } else {
              LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "getDeviceCodes: 响应中缺少 'code' 字段",null);
              throw new IOException("响应中缺少 'code' 字段");
          }

          // 提取 deviceCode 列表
          JSONArray pageDataArray = responseJson.optJSONObject("data")
                  .optJSONArray("pageData");

          if (pageDataArray == null || pageDataArray.length() == 0) {
              LogFileUtil.logAndWrite(Log.WARN, "ArmCloudApiClient", "getDeviceCodes: 查询结果为空",null);
              return new String[0];
          }

          int length = pageDataArray.length();
          String[] deviceCodes = new String[length];

          for (int i = 0; i < length; i++) {
              JSONObject item = pageDataArray.getJSONObject(i);
              if (!item.has("deviceCode")) {
                  LogFileUtil.logAndWrite(Log.WARN, "ArmCloudApiClient", "getDeviceCodes: 返回对象缺少 'deviceCode' 字段",null);
                  throw new IOException("返回对象缺少 'deviceCode' 字段");
              }
              deviceCodes[i] = item.getString("deviceCode");
          }

          return deviceCodes;

      } catch (JSONException e) {
          LogFileUtil.logAndWrite(Log.ERROR, "ArmCloudApiClient", "getDeviceCodes: 响应解析失败", e);
          throw new IOException("响应解析失败", e);
      }
  }

}
