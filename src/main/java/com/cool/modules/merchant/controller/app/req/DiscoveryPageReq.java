package com.cool.modules.merchant.controller.app.req;

import com.cool.core.exception.CoolException;
import lombok.Data;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收用户端门店和优惠券列表的分页筛选条件
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:04:27
 */
@Data
public class DiscoveryPageReq {
    private Integer page = 1;
    private Integer size = 20;
    private String keyword;
    private Long categoryId;
    private Long merchantId;
    private Long storeId;
    private String type;

    public void validate() {
        if (page == null || page < 1 || size == null || size < 1 || size > 100)
            throw new CoolException("page须大于0，size须为1至100");
        for (Long id : new Long[]{categoryId, merchantId, storeId}) {
            if (id != null && id < 1) throw new CoolException("筛选ID须大于0");
        }
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.length() > 100) throw new CoolException("搜索关键词不能超过100个字符");
        }
        if (type != null && !java.util.Set.of("FULL_REDUCTION", "DISCOUNT", "CASH", "PACKAGE", "EXPERIENCE", "EXCHANGE").contains(type))
            throw new CoolException("优惠券类型无效");
    }
}
