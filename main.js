var message = "任务已启动";

function sendMessage(message) {
    app.sendBroadcast({
        action: "org.autojs.SCRIPT_FINISHED", // 自定义广播 Action
        extras: {
            result: message,
            package: "org.autojs.autojs6"// 将结果附加到广播中
        }
    });
}

sendMessage(message + "点击开始")