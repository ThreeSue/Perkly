package com.cool.modules.merchant.location;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cool.core.exception.CoolException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import static com.cool.modules.merchant.location.LocationModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 签名调用腾讯地点提示与地址解析并返回不包含凭证的业务结果
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:11:44
 */
@Service
public class TencentMapService {
    private final TencentMapProperties properties;
    private final RestClient client;

    @Autowired
    public TencentMapService(TencentMapProperties properties, RestClient.Builder builder) {
        this(properties, builder.requestFactory(factory()).build());
    }

    // 使用独立传输客户端，可在测试中模拟腾讯响应，不消耗真实调用配额。
    TencentMapService(TencentMapProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    private static SimpleClientHttpRequestFactory factory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return factory;
    }

    public PlaceResult suggest(String keyword, String region) {
        keyword = text(keyword, "地点关键词", 100, true);
        region = text(region, "城市名称", 50, false);
        var params = new TreeMap<String, String>();
        params.put("keyword", keyword);
        params.put("page_index", "1");
        params.put("page_size", "20");
        if (!region.isEmpty()) { params.put("region", region); params.put("region_fix", "1"); }
        JSONObject json = call("/ws/place/v1/suggestion", params);
        JSONArray data = json.getJSONArray("data");
        if (data == null) throw new CoolException("地图服务返回数据不完整，请稍后重试");
        List<Place> places = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject poi = data.getJSONObject(i), location = poi.getJSONObject("location");
            if (location == null) continue;
            places.add(new Place(poi.getStr("id"), poi.getStr("title"), poi.getStr("address"),
                    location.getBigDecimal("lng"), location.getBigDecimal("lat"),
                    poi.getStr("province"), poi.getStr("city"), poi.getStr("district")));
        }
        return new PlaceResult("GCJ-02", json.getLong("count", (long) places.size()), places);
    }

    public AddressResult geocode(String address, String region) {
        var params = new TreeMap<String, String>();
        params.put("address", text(address, "地址", 500, true));
        String city = text(region, "城市名称", 50, false);
        if (!city.isEmpty()) params.put("region", city);
        JSONObject result = call("/ws/geocoder/v1", params).getJSONObject("result");
        if (result == null || result.getJSONObject("location") == null)
            throw new CoolException("未找到该地址的位置，请调整地址或在地图上手动选点");
        JSONObject location = result.getJSONObject("location"), component = component(result);
        return address(params.get("address"), location, component);
    }

    public AddressResult reverse(BigDecimal longitude, BigDecimal latitude) {
        coordinate(longitude, latitude);
        var params = new TreeMap<String, String>();
        params.put("location", latitude.toPlainString() + "," + longitude.toPlainString());
        JSONObject result = call("/ws/geocoder/v1", params).getJSONObject("result");
        if (result == null) throw new CoolException("未找到该坐标对应的地址");
        JSONObject location = new JSONObject().set("lng", longitude).set("lat", latitude);
        return address(result.getStr("address"), location, component(result));
    }

    public AddressResult translateGps(BigDecimal longitude, BigDecimal latitude) {
        coordinate(longitude, latitude);
        var params = new TreeMap<String, String>();
        params.put("locations", latitude.toPlainString() + "," + longitude.toPlainString());
        params.put("type", "1");
        JSONArray locations = call("/ws/coord/v1/translate", params).getJSONArray("locations");
        if (locations == null || locations.size() != 1) throw new CoolException("坐标转换失败，请在地图上选点");
        JSONObject point = locations.getJSONObject(0);
        coordinate(point.getBigDecimal("lng"), point.getBigDecimal("lat"));
        return address(null, point, new JSONObject());
    }

    private JSONObject component(JSONObject result) {
        JSONObject component = result.getJSONObject("address_components");
        if (component == null) component = result.getJSONObject("address_component");
        return component == null ? new JSONObject() : component;
    }

    private AddressResult address(String address, JSONObject location, JSONObject component) {
        return new AddressResult("GCJ-02", address, location.getBigDecimal("lng"), location.getBigDecimal("lat"),
                component.getStr("province"), component.getStr("city"), component.getStr("district"));
    }

    static String signature(String path, SortedMap<String, String> params, String secret) {
        String query = params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return DigestUtils.md5DigestAsHex((path + "?" + query + secret).getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject call(String path, TreeMap<String, String> params) {
        if (properties.getKey() == null || properties.getKey().isBlank()
                || properties.getSecretKey() == null || properties.getSecretKey().isBlank())
            throw new CoolException("地图服务尚未配置，请联系管理员");
        params.put("key", properties.getKey());
        String sig = signature(path, params, properties.getSecretKey());
        var uri = UriComponentsBuilder.fromHttpUrl("https://apis.map.qq.com").path(path);
        // 参数值用URI变量严格编码，签名仍基于未编码原始值，防止中文、空格、&、+破坏参数。
        var values = new HashMap<String, Object>();
        int index = 0;
        for (var entry : params.entrySet()) {
            String variable = "p" + index++;
            uri.queryParam(entry.getKey(), "{" + variable + "}");
            values.put(variable, entry.getValue());
        }
        uri.queryParam("sig", sig);
        JSONObject json;
        try {
            String body = client.get().uri(uri.encode().buildAndExpand(values).toUri()).retrieve().body(String.class);
            json = JSONUtil.parseObj(body);
        } catch (Exception unavailable) {
            // 不透传HTTP异常：异常消息可能带有含Key和sig的完整请求URL。
            throw new CoolException("地图服务暂不可用，请稍后重试");
        }
        Integer status = json.getInt("status");
        if (status == null) throw new CoolException("地图服务返回数据不完整，请稍后重试");
        if (status != 0) {
            if (Set.of(110, 111, 112, 120, 121, 160, 161).contains(status))
                throw new CoolException("地图服务授权或配额异常，请联系管理员（" + status + "）");
            throw new CoolException("地图查询失败，请调整查询条件或稍后重试（" + status + "）");
        }
        return json;
    }
}
