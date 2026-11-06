package com.cool.modules.space.service;

import com.cool.core.exception.CoolException;
import com.cool.core.file.strategy.LocalFileUploadStrategy;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.space.entity.SpaceInfoEntity;
import com.cool.modules.space.entity.SpaceTypeEntity;
import com.cool.modules.space.mapper.SpaceInfoMapper;
import com.cool.modules.space.mapper.SpaceTypeMapper;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 管理商家共享图片、分类及优惠券和门店封面引用
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 10:49:09
 */
@Service
@RequiredArgsConstructor
public class MerchantMaterialService {
    private final SpaceInfoMapper images;
    private final SpaceTypeMapper categories;
    private final CouponTemplateMapper coupons;
    private final MerchantStoreMapper stores;
    private final WorkspaceAuthorization authorization;
    private final LocalFileUploadStrategy files;
    private final MerchantInfoMapper merchants;

    public record Material(Long id, String url, String name, Integer size, Integer categoryId,
                           Long uploaderId, Date createTime, String source,Long aiTaskId,Long parentMaterialId) {}
    public record Category(Long id, String name) {}
    public record MaterialPage(List<Material> list, long total, long pageNumber, long pageSize) {}
    public record ImportResult(int imported, int skipped) {}

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public ImportResult importCovers(Long actor) {
        Long merchant = merchant(actor, true);
        // 同商家的重复导入串行执行；只读取该商家已有业务封面。
        if (merchants.selectOneByQuery(QueryWrapper.create()
            .eq(MerchantInfoEntity::getId, merchant).forUpdate()) == null) {
            throw new CoolException("商家不存在", 403);
        }
        var covers = new java.util.LinkedHashMap<String, String>();
        coupons.selectListByQuery(QueryWrapper.create().eq(CouponTemplateEntity::getMerchantId, merchant))
            .forEach(c -> { if (c.getCover() != null && !c.getCover().isBlank())
                covers.putIfAbsent(c.getCover(), "优惠券封面-" + c.getName()); });
        stores.selectListByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId, merchant))
            .forEach(s -> { if (s.getCover() != null && !s.getCover().isBlank())
                covers.putIfAbsent(s.getCover(), "门店封面-" + s.getName()); });
        int imported = 0;
        for (var cover : covers.entrySet()) {
            String key = fileName(cover.getKey());
            if (key.isBlank() || key.length() > 255 || !key.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g|gif)$")) continue;
            // 已删除、已登记或归属有冲突的文件均跳过，避免恢复已删除素材或改变归属。
            if (images.selectCountByQuery(QueryWrapper.create().eq(SpaceInfoEntity::getFileId, key)) > 0
                || images.selectCountByQuery(QueryWrapper.create().eq(SpaceInfoEntity::getUrl, cover.getKey())) > 0) continue;
            var material = new SpaceInfoEntity();
            material.setMerchantId(merchant);
            material.setMaterialDeleted(false);
            material.setUrl(cover.getKey());
            material.setFileId(key);
            String name = cover.getValue();
            material.setName(name.substring(0, Math.min(100, name.length())));
            material.setType("image");
            material.setCreateTime(new Date());
            images.insertSelective(material);
            imported++;
        }
        return new ImportResult(imported, covers.size() - imported);
    }

    public MaterialPage page(Long actor, int pageNumber, int pageSize, String keyword, Long categoryId) {
        return page(actor,pageNumber,pageSize,keyword,categoryId,null);
    }
    public MaterialPage page(Long actor, int pageNumber, int pageSize, String keyword, Long categoryId,String source) {
        Long merchant = merchant(actor, false);
        var query = activeImages(merchant).orderBy(SpaceInfoEntity::getId, false);
        filterSource(query,source);
        if (categoryId != null) {
            Integer category = categoryId(merchant, categoryId, false);
            if (category == null) query.isNull(SpaceInfoEntity::getClassifyId);
            else query.eq(SpaceInfoEntity::getClassifyId, category);
        }
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 100) throw new CoolException("搜索词不能超过100字", 400);
            query.like(SpaceInfoEntity::getName, keyword.strip());
        }
        var result = images.paginate(new Page<SpaceInfoEntity>(Math.max(1, pageNumber),
            Math.min(100, Math.max(1, pageSize))), query);
        return new MaterialPage(result.getRecords().stream().map(this::view).toList(),
            result.getTotalRow(), result.getPageNumber(), result.getPageSize());
    }

    public Material info(Long actor, Long id) {
        return view(requireOwned(merchant(actor, false), image(id, false)));
    }

    @Transactional(rollbackFor = Exception.class)
    public Material upload(Long actor, Long categoryId, MultipartFile file, HttpServletRequest request) {
        Long merchant = merchant(actor, true);
        try {
            LocalFileUploadStrategy.validateImage(file);
        } catch (CoolException invalid) {
            throw new CoolException(invalid.getMessage(), 400);
        }
        Integer category = categoryId(merchant, categoryId, true);
        String name = fileName(file.getOriginalFilename());
        if (name.length() > 100) throw new CoolException("文件名不能超过100字", 400);
        String url = (String) files.upload(new MultipartFile[]{file}, request, null);
        String key = fileName(url);
        if (key.isBlank()) throw new CoolException("图片上传失败", 500);
        var material = new SpaceInfoEntity();
        material.setMerchantId(merchant);
        material.setUploaderId(actor);
        material.setMaterialDeleted(false);
        material.setUrl(url);
        material.setFileId(key);
        material.setName(name);
        material.setSize(Math.toIntExact(file.getSize()));
        material.setClassifyId(category);
        material.setType("image");
        material.setCreateTime(new Date());
        images.insertSelective(material);
        return view(material);
    }

    @Transactional(rollbackFor = Exception.class)
    public void move(Long actor, Long id, Long categoryId) {
        Long merchant = merchant(actor, true);
        var material = requireOwned(merchant, image(id, true));
        material.setClassifyId(categoryId(merchant, categoryId, true));
        images.update(material, false);
    }

    // 先锁素材，再以 READ COMMITTED 检查引用。业务保存持同一行锁直到提交。
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void delete(Long actor, Long id) {
        var material = requireOwned(merchant(actor, true), image(id, true));
        String url = material.getUrl();
        if (coupons.selectCountByQuery(QueryWrapper.create().eq(CouponTemplateEntity::getCover, url)) > 0
            || stores.selectCountByQuery(QueryWrapper.create().eq(MerchantStoreEntity::getCover, url)) > 0) {
            throw new CoolException("素材已用作优惠券或门店封面，请先更换业务封面", 409);
        }
        material.setMaterialDeleted(true);
        images.update(material);
    }

    public List<Category> categories(Long actor) {
        Long merchant = merchant(actor, false);
        return categories.selectListByQuery(QueryWrapper.create().eq(SpaceTypeEntity::getMerchantId, merchant)
            .orderBy(SpaceTypeEntity::getId, true)).stream().map(c -> new Category(c.getId(), c.getName())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Category saveCategory(Long actor, Long id, String name) {
        Long merchant = merchant(actor, true);
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new CoolException("分类名称须为1至100字", 400);
        }
        var category = id == null ? new SpaceTypeEntity() : category(merchant, id, true);
        category.setMerchantId(merchant);
        category.setName(name.strip());
        if (id == null) categories.insertSelective(category);
        else categories.update(category);
        return new Category(category.getId(), category.getName());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void deleteCategory(Long actor, Long id) {
        Long merchant = merchant(actor, true);
        var category = category(merchant, id, true);
        if (images.selectCountByQuery(activeImages(merchant)
            .eq(SpaceInfoEntity::getClassifyId, Math.toIntExact(category.getId()))) > 0) {
            throw new CoolException("分类仍有素材，请先移动或删除素材", 409);
        }
        categories.deleteById(id);
    }

    /** 由优惠券和门店的保存事务调用，兼容旧 URL 并锁定库内图片。 */
    public String resolveCover(Long merchant, Long id, String url) {
        var material = coverMaterial(id, url);
        return material == null ? url : requireOwned(merchant, material).getUrl();
    }

    public String selectCover(Long actor, Long merchant, Long id, String url, String previousCover) {
        var material = coverMaterial(id, url);
        if (material == null) return url;
        String selected = requireOwned(merchant, material).getUrl();
        if (!Objects.equals(previousCover, selected)) authorization.require(actor, "material:view");
        return selected;
    }

    private SpaceInfoEntity coverMaterial(Long id, String url) {
        if (id != null) {
            var material = image(id, true);
            if (material == null) throw new CoolException("素材不存在或无权访问", 403);
            return material;
        }
        String key = fileName(url);
        if (key.isBlank() || key.length() > 255) return null;
        return images.selectOneByQuery(QueryWrapper.create().isNotNull(SpaceInfoEntity::getMerchantId)
            .eq(SpaceInfoEntity::getFileId, key).forUpdate());
    }

    private Long merchant(Long actor, boolean manage) {
        var context = authorization.context(actor);
        if (!context.permissions().contains("material:view")
            || manage && !context.permissions().contains("material:manage")) {
            throw new CoolException("没有素材库操作权限", 403);
        }
        return context.merchantId();
    }

    private SpaceInfoEntity image(Long id, boolean lock) {
        if (id == null || id <= 0) return null;
        var query = QueryWrapper.create().eq(SpaceInfoEntity::getId, id);
        return images.selectOneByQuery(lock ? query.forUpdate() : query);
    }

    private SpaceInfoEntity requireOwned(Long merchant, SpaceInfoEntity material) {
        if (material == null || merchant == null || !merchant.equals(material.getMerchantId())) {
            throw new CoolException("素材不存在或无权访问", 403);
        }
        if (Boolean.TRUE.equals(material.getMaterialDeleted())) throw new CoolException("素材已删除，请重新选图", 409);
        return material;
    }

    private SpaceTypeEntity category(Long merchant, Long id, boolean lock) {
        if (id == null || id <= 0 || id > Integer.MAX_VALUE) throw new CoolException("分类不存在或无权访问", 403);
        var query = QueryWrapper.create().eq(SpaceTypeEntity::getId, id).eq(SpaceTypeEntity::getMerchantId, merchant);
        var category = categories.selectOneByQuery(lock ? query.forUpdate() : query);
        if (category == null) throw new CoolException("分类不存在或无权访问", 403);
        return category;
    }

    private Integer categoryId(Long merchant, Long id, boolean lock) {
        return id == null || id == 0 ? null : Math.toIntExact(category(merchant, id, lock).getId());
    }

    private QueryWrapper activeImages(Long merchant) {
        return QueryWrapper.create().eq(SpaceInfoEntity::getMerchantId, merchant)
            .eq(SpaceInfoEntity::getMaterialDeleted, false);
    }

    private Material view(SpaceInfoEntity e) {
        return new Material(e.getId(), e.getUrl(), e.getName(), e.getSize(), e.getClassifyId(), e.getUploaderId(), e.getCreateTime(),
            Objects.toString(e.getSource(),"UPLOAD"),e.getAiTaskId(),e.getParentMaterialId());
    }
    public static void filterSource(QueryWrapper query,String source){
        if(source==null || source.isBlank() || "ALL".equals(source))return;
        if("AI".equals(source))query.eq(SpaceInfoEntity::getSource,"AI");
        else if("UPLOAD".equals(source))query.and((QueryWrapper q)->{q.isNull(SpaceInfoEntity::getSource)
            .or((QueryWrapper other)->{other.eq(SpaceInfoEntity::getSource,"UPLOAD");});});
        else throw new CoolException("素材来源筛选无效",400);
    }

    private String fileName(String value) {
        if (value == null) return "";
        String path = value.strip().replaceAll("[\\r\\n\\t]", "").split("[?#]", 2)[0];
        try {
            path = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // 原有外链格式不作强制更改，仍尝试匹配未编码文件名。
        }
        path = path.replace('\\', '/');
        return path.substring(path.lastIndexOf('/') + 1).split(";", 2)[0];
    }
}
