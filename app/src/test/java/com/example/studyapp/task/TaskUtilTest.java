package com.example.studyapp.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TaskUtilTest {

  @Test
  public void testPostDeviceInfo() throws Exception {
    OkHttpClient mockClient = mock(OkHttpClient.class);
    TaskUtil.setHttpClient(mockClient); // 确保 mockClient 被 TaskUtil 使用

    // Mock Call 和 Response
    Call mockCall = mock(Call.class);
    Response mockResponse = new Response.Builder()
        .request(new Request.Builder().url("http://192.168.1.121:5000/device_info_upload").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(MediaType.get("application/json; charset=utf-8"), "Success"))
        .build();

    // 配置 Mock 行为
    when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
    doAnswer(invocation -> {
      Callback callback = invocation.getArgument(0);
      callback.onResponse(mockCall, mockResponse); // 模拟请求成功的回调
      return null;
    }).when(mockCall).enqueue(any(Callback.class));

    // 调用测试方法
    //TaskUtil.postDeviceInfo();

    // 验证 newCall 是否被调用
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockClient).newCall(requestCaptor.capture()); // 确认调用
    Request capturedRequest = requestCaptor.getValue();

    // 验证请求内容
    assertNotNull(capturedRequest);
    assertEquals("POST", capturedRequest.method());
    assertEquals("http://192.168.1.121:5000/device_info_upload", capturedRequest.url().toString());
    assertNotNull(capturedRequest.body());

    // 验证提交数据 JSON
    Buffer buffer = new Buffer();
    capturedRequest.body().writeTo(buffer);
    String body = buffer.readUtf8();
    assertTrue(body.contains("\"bigoDeviceObject\""));
    assertTrue(body.contains("\"afDeviceObject\""));
  }

  @Test
  public void testPostDeviceInfoDownload_FailedRequest() throws Exception {
    OkHttpClient mockClient = mock(OkHttpClient.class);
    Call mockCall = mock(Call.class);

    when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);

    doAnswer(invocation -> {
      Callback callback = invocation.getArgument(0);
      callback.onFailure(mockCall, new IOException("Request failed"));
      return null;
    }).when(mockCall).enqueue(any(Callback.class));

    //TaskUtil.postDeviceInfo();

    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockClient).newCall(requestCaptor.capture());
    Request capturedRequest = requestCaptor.getValue();

    assertNotNull(capturedRequest);
    assertEquals("POST", capturedRequest.method());
    assertEquals("http://192.168.1.121:5000/device_info_upload", capturedRequest.url().toString());
    assertNotNull(capturedRequest.body());

    // Validate JSON body
    Buffer buffer = new Buffer();
    capturedRequest.body().writeTo(buffer);
    String body = buffer.readUtf8();
    assertTrue(body.contains("\"bigoDeviceObject\""));
    assertTrue(body.contains("\"afDeviceObject\""));
  }
}