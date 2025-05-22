package com.example.studyapp.service;

import com.example.studyapp.request.ScriptResultRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface CloudPhoneManageService {

    // 假设服务端接口接收 POST 请求
    @POST("/api/script/result")
    Call<Void> sendScriptResult(@Body ScriptResultRequest request);

}
