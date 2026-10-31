package com.cool.modules.merchant.location;

import com.cool.core.exception.CoolException;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义门店地图、位置补录、附近查询与腾讯地点服务的数据契约
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:10:45
 */
public final class LocationModels {
    private LocationModels() {}

    public static void coordinate(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null || latitude == null || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0
                || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0)
            throw new CoolException("请提供有效的经纬度，longitude为-180至180，latitude为-90至90");
    }

    public static String text(String value, String label, int max, boolean required) {
        String result = value == null ? "" : value.trim();
        if ((required && result.isEmpty()) || result.length() > max)
            throw new CoolException(label + (required ? "不能为空且" : "") + "不能超过" + max + "个字符");
        return result;
    }

    @Data
    public static class MapQuery {
        private String keyword;
        private Long merchantId;
        private Long storeTypeId;
        private Integer status;
        private Boolean located;

        public void validate() {
            keyword = text(keyword, "关键词", 100, false);
            if ((merchantId != null && merchantId < 1) || (storeTypeId != null && storeTypeId < 1))
                throw new CoolException("筛选ID须大于0");
            if (status != null && !com.cool.modules.merchant.service.StoreStatus.valid(status)) throw new CoolException("门店状态无效");
        }
    }

    @Data
    public static class LocationUpdate {
        private Long id;
        private String address;
        private BigDecimal longitude;
        private BigDecimal latitude;

        public void validate() {
            if (id == null || id < 1) throw new CoolException("请提供有效的门店ID");
            address = text(address, "门店地址", 500, true);
            coordinate(longitude, latitude);
        }
    }

    @Data
    public static class NearbyQuery {
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer radiusMeters = 10000;
        private Integer page = 1;
        private Integer size = 20;
        private Long merchantId;
        // 与已有公开门店接口一致：商家分类ID（同时包含下一级）。
        private Long categoryId;
        private String keyword;

        public void validate() {
            coordinate(longitude, latitude);
            if (radiusMeters == null || radiusMeters < 1 || radiusMeters > 100000)
                throw new CoolException("搜索半径须为1至100000米");
            if (page == null || page < 1 || size == null || size < 1 || size > 100)
                throw new CoolException("page须大于0，size须为1至100");
            if ((merchantId != null && merchantId < 1) || (categoryId != null && categoryId < 1))
                throw new CoolException("筛选ID须大于0");
            keyword = text(keyword, "关键词", 100, false);
        }
    }

    @Data
    public static class StorePoint {
        private Long id;
        private String name;
        private Long merchantId;
        private String merchantName;
        private Long storeTypeId;
        private String storeTypeName;
        private String cover;
        private String address;
        private String phone;
        private String businessHours;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private Integer status;
        private String statusText;
        private boolean located;
        private String locationStateText;
        private Double distanceMeters;
    }

    public record MapResult(String coordinateSystem, long total, long locatedCount,
                            long unlocatedCount, List<StorePoint> list) {}
    public record Place(String id, String title, String address, BigDecimal longitude, BigDecimal latitude,
                        String province, String city, String district) {}
    public record PlaceResult(String coordinateSystem, long total, List<Place> list) {}
    public record AddressResult(String coordinateSystem, String address, BigDecimal longitude,
                                BigDecimal latitude, String province, String city, String district) {}
}
