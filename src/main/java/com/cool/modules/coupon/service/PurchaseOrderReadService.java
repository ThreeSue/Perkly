package com.cool.modules.coupon.service;
import com.cool.core.request.PageResult;
import com.cool.modules.coupon.controller.admin.req.PurchaseOrderQuery;
import com.cool.modules.coupon.controller.admin.resp.PurchaseOrderView;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义运营购券订单只读分页及详情查询能力
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */

public interface PurchaseOrderReadService {
    PageResult<PurchaseOrderView> page(PurchaseOrderQuery input);
    PurchaseOrderView info(Long id);
}
