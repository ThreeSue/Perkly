package com.cool.modules.ai.service;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.core.enums.UserTypeEnum;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 同时校验独立AI权限与实际优惠券编辑权限并构造可信表单上下文
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:35:48
 */
@Service @RequiredArgsConstructor
public class AiAccessService {
    private final SupportAccess access;
    private final WorkspaceAuthorization auth;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;
    private final CouponTemplateMapper coupons;
    public record Context(Long actorId,String actorKind,Long merchantId) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiCoverContent")
    public record Content(String name,Integer quantity,String itemDescription) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="AiCoverDraft")
    public record Draft(Long couponId,Long merchantId,String name,String type,String description,
        Integer scopeType,List<Long> storeIds,List<Content> contentItems,
        BigDecimal minSpend,BigDecimal discountAmount,BigDecimal deductAmount,BigDecimal discountRate) {}
    public record Prepared(Context context,Draft draft,String merchantName,List<String> storeNames,String suggestedPrompt) {}
    public Context libraryContext(Long actor,boolean platform,Long merchant) {
        if(platform) {
            access.platform("ai:image:generate");
            try{access.platform("coupon:template:update");}catch(CoolException denied){access.platform("coupon:template:add");}
        } else {
            var c=auth.context(actor);auth.require(actor,"ai:image:generate");auth.require(actor,"material:view");
            if(!c.permissions().contains("coupon:create") && !c.permissions().contains("coupon:update"))
                throw new CoolException("没有优惠券创建或修改权限",403);
            if(!Objects.equals(merchant,c.merchantId()))throw new CoolException("无法访问其他商家素材",403);
        }
        if(merchant==null || merchants.selectOneById(merchant)==null)throw new CoolException("商家不存在",404);
        return new Context(actor,platform?"PLATFORM":"WORKSPACE",merchant);
    }
    public Context context(Long actor,boolean platform,Long merchant,Long couponId) {
        if(platform) {
            access.platform("ai:image:generate");
            access.platform(couponId==null?"coupon:template:add":"coupon:template:update");
        } else {
            var c=auth.context(actor);
            auth.require(actor,"ai:image:generate");auth.require(actor,"material:view");
            auth.require(actor,couponId==null?"coupon:create":"coupon:update");
            if(merchant==null || !merchant.equals(c.merchantId()))throw new CoolException("无法访问其他商家的AI素材",403);
        }
        var m=merchant==null?null:merchants.selectOneById(merchant);
        if(m==null || !Integer.valueOf(1).equals(m.getStatus()))throw new CoolException("请选择正常商家",403);
        if(couponId!=null) {
            var coupon=coupons.selectOneById(couponId);
            if(coupon==null || !merchant.equals(coupon.getMerchantId()))throw new CoolException("优惠券不在当前商家范围",403);
        }
        return new Context(actor,platform?"PLATFORM":"WORKSPACE",merchant);
    }
    public Prepared prepare(Long actor,boolean platform,Draft d) {
        if(d==null)throw new CoolException("请先填写优惠券表单");
        var c=context(actor,platform,d.merchantId(),d.couponId());
        if(d.name()==null || d.name().isBlank() || d.name().length()>100
            || d.type()==null || !Set.of("CASH","DISCOUNT","FULL_REDUCTION","PACKAGE","EXPERIENCE","EXCHANGE").contains(d.type()))
            throw new CoolException("请先填写券名称和券类型");
        if(d.description()!=null && d.description().length()>2000)throw new CoolException("使用说明过长");
        var items=d.contentItems()==null?List.<Content>of():d.contentItems();
        if(items.size()>100 || items.stream().anyMatch(i->i==null || i.name()==null || i.name().isBlank()
            || i.name().length()>100 || i.quantity()==null || i.quantity()<1 || i.itemDescription()!=null && i.itemDescription().length()>500))
            throw new CoolException("请补全实际商品或服务名称及数量，AI不会猜测未填写的内容");
        var query=QueryWrapper.create().eq(MerchantStoreEntity::getMerchantId,d.merchantId()).eq(MerchantStoreEntity::getStatus,1);
        var selected=d.storeIds()==null?List.<Long>of():d.storeIds().stream().distinct().toList();
        if(selected.size()>100 || selected.stream().anyMatch(Objects::isNull))throw new CoolException("门店范围无效");
        if(Integer.valueOf(1).equals(d.scopeType())) {
            if(selected.isEmpty())throw new CoolException("请选择适用门店");
            query.in(MerchantStoreEntity::getId,selected);
        } else if(d.scopeType()!=null && d.scopeType()!=0)throw new CoolException("门店范围无效");
        var selectedStores=stores.selectListByQuery(query);
        if(selectedStores.isEmpty() || Integer.valueOf(1).equals(d.scopeType()) && selectedStores.size()!=selected.size())
            throw new CoolException("存在其他商家或非营业门店",403);
        if(!platform && !new HashSet<>(auth.storeIds(auth.user(actor))).containsAll(selectedStores.stream().map(MerchantStoreEntity::getId).toList()))
            throw new CoolException("优惠券适用门店超出当前授权范围",403);
        String merchantName=merchants.selectOneById(d.merchantId()).getName();
        String elements=items.isEmpty()?"没有指定实际商品，使用简洁抽象券面元素，不推测菜品、商品或服务外观。":
            "仅呈现这些已填写内容："+items.stream().limit(8).map(i->i.name()+" × "+i.quantity()
                +(i.itemDescription()==null?"":"（"+shortText(i.itemDescription(),100)+"）")).collect(java.util.stream.Collectors.joining("、"))+"。"
                +(items.size()>8?"其余项目不展开描绘，不猜测内容。":"");
        String typeName=switch(d.type()) {case "CASH"->"代金券";case "DISCOUNT"->"折扣券";case "FULL_REDUCTION"->"满减券";
            case "PACKAGE"->"套餐券";case "EXPERIENCE"->"体验券";default->"兑换券";};
        String prompt="为优惠券制作一张1024×1024正方形封面。券名："+d.name()+"。类型："+typeName+"。商家："+shortText(merchantName,100)+"。"
            +"适用门店："+selectedStores.stream().limit(3).map(s->shortText(s.getName(),60)).collect(java.util.stream.Collectors.joining("、"))
            +(selectedStores.size()>3?"等已选门店":"")+"。"+elements
            +"主视觉清晰，适合手机列表缩略图，主体居中，保留安全边距，画面干净；不编造价格、折扣或宣传承诺，不生成二维码、水印或联系方式。"
            +(d.description()==null?"":"使用说明仅作构图参考："+shortText(d.description(),400));
        return new Prepared(c,d,merchantName,selectedStores.stream().map(MerchantStoreEntity::getName).toList(),prompt);
    }
    private String shortText(String value,int limit){return value==null?"":value.substring(0,Math.min(limit,value.length()));}
}
