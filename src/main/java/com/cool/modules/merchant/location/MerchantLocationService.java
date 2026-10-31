package com.cool.modules.merchant.location;

import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.service.MerchantStoreService;
import com.cool.modules.merchant.service.MerchantWorkspaceService;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.cool.modules.merchant.location.LocationModels.*;
import static com.mybatisflex.core.query.QueryMethods.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用MyBatis-Flex查询门店地图和球面距离并按商家归属维护位置
 * @Version: v1.1
 * @Date: 2026-10-24
 * @Time: 14:10:45
 */
@Service
@RequiredArgsConstructor
public class MerchantLocationService {
    private static final double EARTH_RADIUS = 6371008.8;
    private final MerchantStoreMapper stores;
    private final MerchantStoreService storeService;
    private final MerchantWorkspaceService workspace;

    @Transactional(readOnly = true)
    public MapResult adminMap(MapQuery input) { return map(input, null,null); }

    @Transactional(readOnly = true)
    public MapResult merchantMap(Long userId, MapQuery input) {
        // 以服务端身份为准，客户端merchantId不能改变可见范围。
        return map(input, workspace.merchant(userId),workspace.allowedStoreIds(userId));
    }

    private MapResult map(MapQuery input, Long scope,List<Long> allowed) {
        input.validate();
        QueryWrapper query = projection();
        Long merchantId = scope == null ? input.getMerchantId() : scope;
        if (merchantId != null) query.eq(MerchantStoreEntity::getMerchantId, merchantId);
        if(allowed!=null){if(allowed.isEmpty())query.eq(MerchantStoreEntity::getId,-1L);else query.in(MerchantStoreEntity::getId,allowed);}
        if (input.getStoreTypeId() != null) query.eq(MerchantStoreEntity::getCategoryId, input.getStoreTypeId());
        if (input.getStatus() != null) query.eq(MerchantStoreEntity::getStatus, input.getStatus());
        keyword(query, input.getKeyword());
        if (input.getLocated() != null) query.and(input.getLocated() ? validCoordinates() : invalidCoordinates());
        List<StorePoint> list = stores.selectListByQueryAs(query.orderBy(MerchantStoreEntity::getId, true), StorePoint.class);
        list.forEach(this::display);
        long located = list.stream().filter(StorePoint::isLocated).count();
        return new MapResult("GCJ-02", list.size(), located, list.size() - located, list);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateAdmin(LocationUpdate input) { return update(input, null); }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateMerchant(Long userId, LocationUpdate input) {
        workspace.storeInfo(userId,input.getId());
        return update(input, workspace.merchant(userId));
    }

    private boolean update(LocationUpdate input, Long merchantId) {
        input.validate();
        QueryWrapper query = QueryWrapper.create().eq(MerchantStoreEntity::getId, input.getId());
        if (merchantId != null) query.eq(MerchantStoreEntity::getMerchantId, merchantId);
        MerchantStoreEntity existing = stores.selectOneByQuery(query.forUpdate());
        if (existing == null) throw new CoolException("门店不存在或无权修改", 403);
        existing.setAddress(input.getAddress());
        existing.setLongitude(input.getLongitude());
        existing.setLatitude(input.getLatitude());
        return storeService.update(existing);
    }

    @Transactional(readOnly = true)
    public PageResult<StorePoint> nearby(NearbyQuery input) {
        input.validate();
        QueryWrapper query = projection().and(validCoordinates())
                .eq(MerchantStoreEntity::getStatus, 1).eq(MerchantInfoEntity::getStatus, 1);
        if (input.getMerchantId() != null) query.eq(MerchantStoreEntity::getMerchantId, input.getMerchantId());
        if (input.getCategoryId() != null) {
            query.leftJoin(MerchantCategoryEntity.class)
                    .on(column(MerchantCategoryEntity::getId).eq(column(MerchantInfoEntity::getCategoryId)))
                    .and(column(MerchantInfoEntity::getCategoryId).eq(input.getCategoryId())
                            .or(column(MerchantCategoryEntity::getParentId).eq(input.getCategoryId())));
        }
        keyword(query, input.getKeyword());
        // 先按纬度带缩小候选集，再由数据库计算距离、半径筛选、排序和分页。
        double delta = Math.toDegrees(input.getRadiusMeters() / EARTH_RADIUS);
        query.ge(MerchantStoreEntity::getLatitude, Math.max(-90, input.getLatitude().doubleValue() - delta))
                .le(MerchantStoreEntity::getLatitude, Math.min(90, input.getLatitude().doubleValue() + delta));
        query.select(distance(input).as("distanceMeters"))
                .and(distance(input).le(input.getRadiusMeters()))
                .orderBy(distance(input).asc(), column(MerchantStoreEntity::getId).asc());
        var result = stores.paginateAs(input.getPage(), input.getSize(), query, StorePoint.class);
        result.getRecords().forEach(this::display);
        return PageResult.of(result);
    }

    private QueryWrapper projection() {
        return QueryWrapper.create().select(MerchantStoreEntity::getId, MerchantStoreEntity::getName,
                        MerchantStoreEntity::getMerchantId, MerchantStoreEntity::getCover,
                        MerchantStoreEntity::getAddress, MerchantStoreEntity::getPhone,
                        MerchantStoreEntity::getBusinessHours, MerchantStoreEntity::getLongitude,
                        MerchantStoreEntity::getLatitude, MerchantStoreEntity::getStatus)
                .select(column(MerchantInfoEntity::getName).as("merchantName"),
                        column(MerchantStoreEntity::getCategoryId).as("storeTypeId"),
                        column(MerchantStoreCategoryEntity::getName).as("storeTypeName"))
                .from(MerchantStoreEntity.class)
                .leftJoin(MerchantInfoEntity.class).on(column(MerchantInfoEntity::getId).eq(column(MerchantStoreEntity::getMerchantId)))
                .leftJoin(MerchantStoreCategoryEntity.class).on(column(MerchantStoreCategoryEntity::getId).eq(column(MerchantStoreEntity::getCategoryId)));
    }

    private QueryCondition validCoordinates() {
        QueryColumn lng = column(MerchantStoreEntity::getLongitude), lat = column(MerchantStoreEntity::getLatitude);
        return lng.isNotNull(true).and(lat.isNotNull(true)).and(lng.ge(-180)).and(lng.le(180))
                .and(lat.ge(-90)).and(lat.le(90));
    }

    private QueryCondition invalidCoordinates() {
        QueryColumn lng = column(MerchantStoreEntity::getLongitude), lat = column(MerchantStoreEntity::getLatitude);
        return lng.isNull(true).or(lat.isNull(true)).or(lng.lt(-180)).or(lng.gt(180))
                .or(lat.lt(-90)).or(lat.gt(90));
    }

    private void keyword(QueryWrapper query, String keyword) {
        if (!keyword.isEmpty()) query.and(column(MerchantStoreEntity::getName).like(keyword)
                .or(column(MerchantInfoEntity::getName).like(keyword))
                .or(column(MerchantStoreEntity::getAddress).like(keyword)));
    }

    private QueryColumn distance(NearbyQuery input) {
        // Haversine；限制浮点误差，重合点不会产生ACOS的NULL，跨日期变更线也可正常计算。
        QueryColumn lat = radians(MerchantStoreEntity::getLatitude);
        QueryColumn lng = radians(MerchantStoreEntity::getLongitude);
        // ArithmeticQueryColumn会平铺连续运算；先各自除以2再相减，确保生成SQL的优先级正确。
        QueryColumn a = pow(sin(lat.divide(2).subtract(Math.toRadians(input.getLatitude().doubleValue()) / 2)), 2)
                .add(cos(radians(MerchantStoreEntity::getLatitude))
                        .multiply(Math.cos(Math.toRadians(input.getLatitude().doubleValue())))
                        .multiply(pow(sin(lng.divide(2).subtract(Math.toRadians(input.getLongitude().doubleValue()) / 2)), 2)));
        QueryColumn safe = case_().when(a.lt(0)).then(0).when(a.gt(1)).then(1).else_(a).end();
        return asin(sqrt(safe)).multiply(2 * EARTH_RADIUS);
    }

    private void display(StorePoint point) {
        boolean located = point.getLongitude() != null && point.getLatitude() != null
                && point.getLongitude().abs().doubleValue() <= 180 && point.getLatitude().abs().doubleValue() <= 90;
        point.setLocated(located);
        point.setLocationStateText(located ? "已设置位置" : "待补充位置");
        point.setStatusText(com.cool.modules.merchant.service.StoreStatus.text(point.getStatus()));
    }
}
