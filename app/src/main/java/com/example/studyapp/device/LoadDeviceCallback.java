package com.example.studyapp.device;

import org.json.JSONObject;

public interface LoadDeviceCallback{
  void onLoadDeviceInfo(JSONObject bigoDevice, JSONObject afDevice);
}
