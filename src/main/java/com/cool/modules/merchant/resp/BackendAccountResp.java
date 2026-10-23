package com.cool.modules.merchant.resp;

import java.util.List;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供不包含密码及会话信息的后台账号和商家门店分组视图
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 22:10:23
 */
public final class BackendAccountResp {
    private BackendAccountResp() {}
    public record Account(Long id, String username, String name, String role, Integer status,
                          Long merchantId, String merchantName, Long storeId, String storeName, boolean bindingValid) {}
    public record Pagination(int page, int size, long total) {}
    public record AccountPage(List<Account> list, Pagination pagination) {}
    public record Group(Long merchantId, String name, List<Store> stores, Long categoryId) {}
    public record Store(Long id, String name) {}
}
