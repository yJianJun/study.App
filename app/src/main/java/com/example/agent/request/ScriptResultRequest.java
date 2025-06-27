package com.example.agent.request;

// 这是发送到服务端的请求体（JSON 格式）
public class ScriptResultRequest {
    private String result;

    public ScriptResultRequest(String result) {
        this.result = result;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
