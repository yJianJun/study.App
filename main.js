function promiseHttpLibGet(url) {
    return new Promise((resolve, reject) => {
        http.get(url, {}, (res, err) => {
            if (err) {
                reject(err);
            } else {
                resolve(res);
            }
        });
    });
}

async function processIpApi(url, successCallback, errorMsgPrefix) {
    try {
        const res = await promiseHttpLibGet(url); // 使用 await等待Promise结果

        if (res && res.statusCode == 200) {
            try {
                let data = res.body.json();
                successCallback(data);
            } catch (jsonError) {
                toast(errorMsgPrefix + "解析JSON失败");
                console.error(errorMsgPrefix + "解析JSON失败: ", jsonError);
                 if (res.body && typeof res.body.string === 'function') {
                    console.log("原始响应体: ", res.body.string());
                }
            }
        } else if (res) {
            toast(errorMsgPrefix + "获取失败，状态码: " + res.statusCode);
            console.log(errorMsgPrefix + "获取失败，状态码: " + res.statusCode);
            if (res.body && typeof res.body.string === 'function') {
                console.log("原始响应体: ", res.body.string());
            }
        } else {
             toast(errorMsgPrefix + "请求没有返回有效响应");
            console.log(errorMsgPrefix + "请求没有返回有效响应");
        }
    } catch (requestError) {
        console.error(errorMsgPrefix + "请求失败: ", requestError);
        toast(errorMsgPrefix + "请求失败，请检查网络");
    }
}

async function main() {
    // 并行发起两个请求
    const countryPromise = processIpApi("https://ipv4.geojs.io/v1/ip/country.json", function(data2){
        toast("国家代码: " + data2.country);
        console.log("地理位置信息：");
        console.log("国家代码 (2位): " + data2.country);
        console.log("国家代码 (3位): " + data2.country_3);
        console.log("IP 地址: " + data2.ip);
        console.log("国家名称: " + data2.name);
    }, "地理位置");

    const geoPromise = processIpApi("https://ipv4.geojs.io/v1/ip/geo.json", function(data3){
        console.log("新增接口返回的地理数据：");
        console.log("经度: " + data3.longitude);
        console.log("纬度: " + data3.latitude);
        console.log("城市: " + data3.city);
        console.log("地区: " + data3.region);
        console.log("国家: " + data3.country);
        console.log("组织名称: " + data3.organization_name);
        console.log("IP 地址: " + data3.ip);
        console.log("国家代码 (2位): " + data3.country_code);
        console.log("国家代码 (3位): " + data3.country_code3);
        console.log("时区: " + data3.timezone);
        console.log("ASN: " + data3.asn);
        console.log("洲: " + data3.continent_code);
    }, "详细地理位置");

    // 等待两个请求完成（或失败）
    try {
        await Promise.all([countryPromise, geoPromise]);
    } catch (error) {
        console.error("一个或多个API请求失败：", error);
    }
}



main(); // 执行主函数