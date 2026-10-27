package com.cool.modules.home.service;

import cn.hutool.core.bean.BeanUtil;
import com.cool.core.exception.CoolException;
import com.cool.core.request.PageResult;
import com.cool.modules.home.entity.HomeBannerEntity;
import com.cool.modules.home.mapper.HomeBannerMapper;
import com.cool.modules.merchant.controller.app.req.DiscoveryPageReq;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.mapper.query.AppDiscoveryQueries;
import com.cool.modules.merchant.service.AppDiscoveryService;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.cool.modules.home.model.HomeBannerModels.*;
import static com.mybatisflex.core.query.QueryMethods.column;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理首页广告并按展示时间及目标可用性输出有效轮播
 * @Version: v1.1
 * @Date: 2026-10-27
 * @Time: 15:15:34
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class HomeBannerService {
    private final HomeBannerMapper banners;
    private final MerchantStoreMapper stores;
    private final CouponTemplateMapper coupons;
    private final AppDiscoveryService discovery;

    public Long add(HomeBannerEntity input) {
        if (input.getId() != null) throw new CoolException("新增广告不能指定ID");
        if (input.getSort() == null) input.setSort(0);
        if (input.getStatus() == null) input.setStatus(0);
        input.setCreateTime(null); input.setUpdateTime(null);
        validate(input);
        banners.insertSelective(input);
        return input.getId();
    }

    public boolean update(HomeBannerEntity input) {
        HomeBannerEntity existing = lock(input.getId());
        // 更新使用完整表单；可将时间和目标明确置空，不能被忽略NULL的默认更新吞掉。
        validate(input);
        input.setCreateTime(existing.getCreateTime());
        input.setUpdateTime(new Date());
        return banners.update(input, false) == 1;
    }

    public boolean status(StatusInput input) {
        if (input.status() == null || (input.status() != 0 && input.status() != 1))
            throw new CoolException("状态只能为0或1");
        HomeBannerEntity existing = lock(input.id());
        if (input.status() == 1) {
            existing.setStatus(1);
            validate(existing);
        } else existing.setStatus(0);
        return banners.update(existing) == 1;
    }

    public boolean delete(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id < 1))
            throw new CoolException("请选择1至100条广告");
        List<Long> ordered = ids.stream().distinct().sorted().toList();
        ordered.forEach(this::lock);
        return banners.deleteBatchByIds(ordered) == ordered.size();
    }

    @Transactional(readOnly = true)
    public PageResult<BannerView> page(PageInput input) {
        input.validate();
        QueryWrapper query = QueryWrapper.create();
        if (!input.getKeyword().isEmpty()) query.like(HomeBannerEntity::getTitle, input.getKeyword());
        if (input.getStatus() != null) query.eq(HomeBannerEntity::getStatus, input.getStatus());
        if (input.getJumpType() != null) query.eq(HomeBannerEntity::getJumpType, input.getJumpType());
        var result = banners.paginate(input.getPage(), input.getSize(), ordered(query));
        PageResult<BannerView> response = new PageResult<>();
        response.setList(result.getRecords().stream().map(this::view).toList());
        response.getPagination().setPage(result.getPageNumber());
        response.getPagination().setSize(result.getPageSize());
        response.getPagination().setTotal(result.getTotalRow());
        return response;
    }

    @Transactional(readOnly = true)
    public BannerView info(Long id) {
        positive(id);
        HomeBannerEntity row = banners.selectOneById(id);
        if (row == null) throw new CoolException("广告不存在");
        return view(row);
    }

    @Transactional(readOnly = true)
    public List<PublicBanner> publicList() { return visibleAt(new Date()); }

    List<PublicBanner> visibleAt(Date now) {
        QueryWrapper query = QueryWrapper.create().eq(HomeBannerEntity::getStatus, 1)
                .and(column(HomeBannerEntity::getStartTime).isNull(true).or(column(HomeBannerEntity::getStartTime).le(now)))
                .and(column(HomeBannerEntity::getEndTime).isNull(true).or(column(HomeBannerEntity::getEndTime).gt(now)));
        // 在可用性过滤后取前10个，避免前10个目标下架导致后续有效广告遗漏。
        return banners.selectListByQuery(ordered(query)).stream().filter(this::available).limit(10)
                .map(row -> new PublicBanner(row.getId(), row.getTitle(), row.getImageUrl(), row.getJumpType(), row.getJumpTarget(), row.getSort())).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<TargetOption> targetOptions(String type, String keyword, Integer page) {
        if (!List.of("STORE", "COUPON").contains(type)) throw new CoolException("请选择门店或优惠券类型");
        var input = new DiscoveryPageReq(); input.setKeyword(keyword); input.setPage(page == null ? 1 : page); input.setSize(30); input.validate();
        return "STORE".equals(type)
                ? PageResult.of(stores.paginateAs(input.getPage(), input.getSize(), AppDiscoveryQueries.stores(input), TargetOption.class))
                : PageResult.of(coupons.paginateAs(input.getPage(), input.getSize(), AppDiscoveryQueries.coupons(input), TargetOption.class));
    }

    private QueryWrapper ordered(QueryWrapper query) {
        return query.orderBy(HomeBannerEntity::getSort, true).orderBy(HomeBannerEntity::getId, true);
    }

    private HomeBannerEntity lock(Long id) {
        positive(id);
        HomeBannerEntity row = banners.selectOneByQuery(QueryWrapper.create().eq(HomeBannerEntity::getId, id).forUpdate());
        if (row == null) throw new CoolException("广告不存在");
        return row;
    }

    private BannerView view(HomeBannerEntity row) {
        BannerView result = BeanUtil.copyProperties(row, BannerView.class);
        String target = "不跳转";
        if ("WEB".equals(row.getJumpType())) target = row.getJumpTarget();
        if ("STORE".equals(row.getJumpType())) {
            var store = stores.selectOneById(row.getJumpTarget());
            target = store == null ? "门店已删除" : store.getName();
        }
        if ("COUPON".equals(row.getJumpType())) {
            var coupon = coupons.selectOneById(row.getJumpTarget());
            target = coupon == null ? "优惠券已删除" : coupon.getName();
        }
        result.setTargetName(target);
        Date now = new Date();
        result.setDisplayStateText(row.getStatus() != 1 ? "已停用"
                : row.getStartTime() != null && row.getStartTime().after(now) ? "待开始"
                : row.getEndTime() != null && !row.getEndTime().after(now) ? "已结束"
                : !available(row) ? "目标不可用" : "展示中");
        return result;
    }

    private boolean available(HomeBannerEntity row) {
        try {
            if ("STORE".equals(row.getJumpType())) discovery.store(Long.valueOf(row.getJumpTarget()));
            else if ("COUPON".equals(row.getJumpType())) discovery.coupon(Long.valueOf(row.getJumpTarget()));
            else return "NONE".equals(row.getJumpType()) || "WEB".equals(row.getJumpType());
            return true;
        } catch (CoolException | NumberFormatException unavailable) { return false; }
    }

    private void validate(HomeBannerEntity row) {
        row.setTitle(text(row.getTitle(), "广告名称", 100));
        row.setImageUrl(text(row.getImageUrl(), "广告图片", 1000));
        imageUrl(row.getImageUrl());
        if (row.getJumpType() == null || !List.of("NONE", "STORE", "COUPON", "WEB").contains(row.getJumpType()))
            throw new CoolException("请选择有效的跳转类型");
        if (row.getSort() == null || row.getSort() < 0 || row.getSort() > 999999) throw new CoolException("排序须为0至999999");
        if (row.getStatus() == null || (row.getStatus() != 0 && row.getStatus() != 1)) throw new CoolException("状态只能为0或1");
        if (row.getStartTime() != null && row.getEndTime() != null && !row.getStartTime().before(row.getEndTime()))
            throw new CoolException("结束时间必须晚于开始时间");
        if ("NONE".equals(row.getJumpType())) row.setJumpTarget(null);
        else {
            row.setJumpTarget(text(row.getJumpTarget(), "跳转目标", 1000));
            if ("WEB".equals(row.getJumpType())) webUrl(row.getJumpTarget(), "跳转网页");
            else {
                Long id;
                try { if (!row.getJumpTarget().matches("[1-9][0-9]*")) throw new NumberFormatException(); id = Long.valueOf(row.getJumpTarget()); }
                catch (NumberFormatException invalid) { throw new CoolException("请选择有效的门店或优惠券"); }
                boolean exists = "STORE".equals(row.getJumpType()) ? stores.selectOneById(id) != null : coupons.selectOneById(id) != null;
                if (!exists) throw new CoolException("跳转目标不存在");
                if (row.getStatus() == 1 && !available(row)) throw new CoolException("跳转目标当前不可展示，请先启用门店或完成优惠券上架");
            }
        }
    }

    private static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new CoolException(label + "不能为空且最多" + max + "字");
        return value.trim();
    }
    private static void positive(Long id) { if (id == null || id < 1) throw new CoolException("广告ID无效"); }
    private static void imageUrl(String value) {
        if (value.startsWith("/") && !value.startsWith("//") && !value.contains("\\") && !value.contains("..") && !value.matches(".*[\\s\\p{Cntrl}].*")) return;
        webUrl(value, "图片地址");
    }
    private static void webUrl(String value, String label) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new CoolException(label + "须为有效的HTTP或HTTPS地址"); }
    }
}
