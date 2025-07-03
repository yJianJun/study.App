// ========== 日志写入文件的封装 ==========
function log(...args) {
  const message = `[LOG] ${args.map(a =>
      typeof a === 'object' ? JSON.stringify(a) : a
  ).join(' ')}`;
  console.log(message);
  files.append("/sdcard/output_log.txt", new Date().toISOString() + " " + message + "\n");
}

function error(...args) {
  const message = `[ERROR] ${args.map(a =>
      typeof a === 'object' ? JSON.stringify(a) : a
  ).join(' ')}`;
  console.error(message);
  files.append("/sdcard/output_log.txt", new Date().toISOString() + " " + message + "\n");
}

// ========== 网络请求封装 ==========
function httpGetWithCallbacks(url, successCallback, errorCallback) {
  http.get(url, {}, (res, err) => {
    if (err) {
      if (errorCallback) errorCallback(err);
    } else {
      if (successCallback) successCallback(res);
    }
  });
}

function processIpApiWithCallbacks(url, processSuccessCallback, errorMsgPrefix, apiDoneCallback) {
  httpGetWithCallbacks(url,
      (res) => {
        let operationSuccessful = false;
        if (!res) {
          toast(errorMsgPrefix + "请求没有返回有效响应");
          log(errorMsgPrefix + "请求没有返回有效响应");
          if (apiDoneCallback) apiDoneCallback(new Error("No valid response from request (res is null)"));
          return;
        }

        if (res.statusCode != 200) {
          let errorBody = "";
          try {
            if (res.body && typeof res.body.string === 'function') {
              errorBody = res.body.string(); // 读取并关闭
            }
          } catch (e) {
            error(errorMsgPrefix + "读取错误响应体失败: ", e);
          }
          toast(errorMsgPrefix + "获取失败，状态码: " + res.statusCode);
          log(errorMsgPrefix + "获取失败，状态码: " + res.statusCode + ", Body: " + errorBody);
          if (apiDoneCallback) apiDoneCallback(new Error(errorMsgPrefix + "获取失败，状态码: " + res.statusCode));
          return;
        }

        try {
          let data = res.body.json(); // 读取并关闭
          processSuccessCallback(data);
          operationSuccessful = true;
        } catch (jsonError) {
          toast(errorMsgPrefix + "解析JSON失败");
          error(errorMsgPrefix + "解析JSON失败: ", jsonError);
          if (apiDoneCallback) apiDoneCallback(jsonError);
          return;
        }

        if (apiDoneCallback) apiDoneCallback(null);
      },
      (requestError) => {
        error(errorMsgPrefix + "处理时发生错误: ", requestError);
        if (!toast.isShow()) {
          toast(errorMsgPrefix + "请求或处理失败");
        }
        if (apiDoneCallback) apiDoneCallback(requestError);
      }
  );
}

// ========== 主函数 ==========
function main() {
  log("Main function started.");

  let tasksCompleted = 0;
  const totalTasks = 2;
  let errors = [];

  function checkAllTasksDone() {
    tasksCompleted++;
    if (tasksCompleted === totalTasks) {
      log("All API calls have finished processing.");
      if (errors.length > 0) {
        error("One or more API calls failed:");
        errors.forEach(err => error(err));
        toast("部分API请求失败，请检查日志");
      } else {
        log("All API calls were successful.");
        toast("所有API请求成功");
      }
      log("Script operations concluded. Adding a small delay for UI.");
      sleep(500);
    }
  }

  function handleApiError(error) {
    if (error) {
      errors.push(error);
    }
    checkAllTasksDone();
  }

  // 调用第一个 API
  processIpApiWithCallbacks(
      "https://ipv4.geojs.io/v1/ip/country.json",
      function(data2) {
        toast("国家代码: " + data2.country);
        log("地理位置信息：");
        log("国家代码 (2位): " + data2.country);
        log("国家代码 (3位): " + data2.country_3);
        log("IP 地址: " + data2.ip);
        log("国家名称: " + data2.name);
      },
      "地理位置",
      handleApiError
  );

  // 调用第二个 API
  processIpApiWithCallbacks(
      "https://ipv4.geojs.io/v1/ip/geo.json",
      function(data3) {
        log("新增接口返回的地理数据：");
        log("经度: " + data3.longitude);
        log("纬度: " + data3.latitude);
        log("城市: " + data3.city);
        log("地区: " + data3.region);
        log("国家: " + data3.country);
        log("组织名称: " + data3.organization_name);
        log("IP 地址: " + data3.ip);
        log("国家代码 (2位): " + data3.country_code);
        log("国家代码 (3位): " + data3.country_code3);
        log("时区: " + data3.timezone);
        log("ASN: " + data3.asn);
        log("洲: " + data3.continent_code);
      },
      "详细地理位置",
      handleApiError
  );
}

// ========== 启动主函数并设置定时器 ==========

(function startLoop() {
  const intervalMillis = 60 * 1000; // 每隔 60 秒执行一次

  main(); // 首次启动
  setInterval(main, intervalMillis); // 循环执行
})();
