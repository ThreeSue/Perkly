package com.cool.modules.merchant.controller.admin.params;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义运营端后台账号查询及商家管理员维护参数
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 22:10:23
 */
public final class BackendAccountParam {
    private BackendAccountParam() {}
    public record PageInput(Long merchantId, Long storeId, String role, Integer status, String keyWord, Integer page, Integer size, Long categoryId) {
        public PageInput(Long merchantId, Long storeId, String role, Integer status, String keyWord, Integer page, Integer size) {
            this(merchantId, storeId, role, status, keyWord, page, size, null);
        }
    }
    public record OwnerInput(Long id, String name, Integer status) {}
}
