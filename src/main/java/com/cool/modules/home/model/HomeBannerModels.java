package com.cool.modules.home.model;

import com.cool.core.exception.CoolException;

import java.util.List;

import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义广告分页状态修改及面向用户端的精简响应
 * @Version: v1.1
 * @Date: 2026-10-27
 * @Time: 15:14:03
 */
public final class HomeBannerModels {
    private HomeBannerModels() {
    }

    @Data
    public static class PageInput {
        private Integer page = 1;
        private Integer size = 20;
        private String keyword;
        private Integer status;
        private String jumpType;

        public void validate() {
            if (page == null || page < 1 || page > 100000 || size == null || size < 1 || size > 100)
                throw new CoolException("分页参数无效，size须为1至100");
            keyword = keyword == null ? "" : keyword.trim();
            if (keyword.length() > 100) throw new CoolException("关键词最多100字");
            if (status != null && status != 0 && status != 1) throw new CoolException("状态只能为0或1");
            if (jumpType != null && !List.of("NONE", "STORE", "COUPON", "WEB").contains(jumpType))
                throw new CoolException("跳转类型无效");
        }
    }

    public record IdInput(Long id) {
    }

    public record DeleteInput(List<Long> ids) {
    }

    public record StatusInput(Long id, Integer status) {
    }

    public record PublicBanner(Long id, String title, String imageUrl, String jumpType, String jumpTarget,
                               Integer sort) {
    }

    @lombok.Getter
    @lombok.Setter
    public static class BannerView extends com.cool.modules.home.entity.HomeBannerEntity {
        private String targetName;
        private String displayStateText;
    }

    @Data
    public static class TargetOption {
        private Long id;
        private String name;
        private String merchantName;
    }
}
