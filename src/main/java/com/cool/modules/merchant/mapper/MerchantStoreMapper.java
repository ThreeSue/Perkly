package com.cool.modules.merchant.mapper;

import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import static com.mybatisflex.core.query.QueryMethods.number;
import static com.mybatisflex.core.query.QueryMethods.select;

public interface MerchantStoreMapper extends BaseMapper<MerchantStoreEntity> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM store WHERE id=#{id} FOR UPDATE")
    Long lockForCouponCheck(Long id);

    default long couponReferenceCount(Long id) {
        if (id == null) return 0;
        QueryWrapper references = select(number(1)).from("coupon_store").eq("store_id", id)
                .unionAll(select(number(1)).from("coupon_redemption").eq("store_id", id));
        return selectCountByQuery(QueryWrapper.create().from(references).as("refs"));
    }
}
