package com.cool.modules.ai.management;

import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.merchant.service.*;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 复用分类与门店类型服务进行字段预览引用校验和确认执行
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@RequiredArgsConstructor
public abstract class CatalogAssistantTool implements ManagementAssistantTool {
    protected final MerchantCategoryService categories;
    protected final MerchantStoreCategoryService types;
    protected final ManagementAssistantAccess access;
    public record Value(Long id,String name,Integer status,Integer sort,Long parentId,String icon,Integer showOnFront) {}
    public record Change(Action action,Value before,Value after) {}
    private String permission(String action) {
        return (scope()==Scope.CATEGORY?"merchant:category:":"merchant:storeCategory:")+action;
    }
    private void require(Actor actor,String action) {access.require(actor,permission(action),null);}
    private Value value(MerchantCategoryEntity r) {return new Value(r.getId(),r.getName(),r.getStatus(),r.getSort(),r.getParentId(),r.getIcon(),r.getShowOnFront());}
    private Value value(MerchantStoreCategoryEntity r) {return new Value(r.getId(),r.getName(),r.getStatus(),r.getSort(),null,null,null);}
    private Value current(Long id) {
        if(id==null || id<=0)throw new CoolException("请先选择明确的分类或类型",400);
        if(scope()==Scope.CATEGORY) {var r=categories.getOne(QueryWrapper.create().eq(MerchantCategoryEntity::getId,id).forUpdate());if(r!=null)return value(r);}
        else {var r=types.getOne(QueryWrapper.create().eq(MerchantStoreCategoryEntity::getId,id).forUpdate());if(r!=null)return value(r);}
        throw new CoolException("目标不存在",404);
    }
    @Override public Result query(Actor actor,Step step) {
        require(actor,step.id()==null?"page":"info");
        if(step.id()!=null)return new Result(scope(),"READ","当前真实资料",current(step.id()));
        List<Value> values;
        if(scope()==Scope.CATEGORY) {
            var q=QueryWrapper.create();
            if(step.keyword()!=null && !step.keyword().isBlank())q.like(MerchantCategoryEntity::getName,step.keyword());
            values=categories.list(q.orderBy(MerchantCategoryEntity::getSort,true).limit(101)).stream().map(this::value).toList();
        } else {
            var q=QueryWrapper.create();
            if(step.keyword()!=null && !step.keyword().isBlank())q.like(MerchantStoreCategoryEntity::getName,step.keyword());
            values=types.list(q.orderBy(MerchantStoreCategoryEntity::getSort,true).limit(101)).stream().map(this::value).toList();
        }
        return new Result(scope(),"READ",values.size()>100?"仅展示前100条，不能视为全量":"匹配结果",values.stream().limit(100).toList());
    }
    @Override public Result preview(Actor actor,Step step) {
        String action=switch(step.action()){case CREATE->"add";case UPDATE->"update";case DELETE->"delete";default->throw new CoolException("不支持的分类操作",400);};
        require(actor,action);
        Value before=step.action()==Action.CREATE?null:current(step.id());
        if(step.action()==Action.DELETE)return new Result(scope(),"PREVIEW","删除将在确认时再次校验引用；不会迁移关联资料",new Change(step.action(),before,null));
        JsonNode f=step.fields();
        if(f==null || !f.isObject())throw new CoolException("请说明需要填写的字段",400);
        Set<String> allowed=scope()==Scope.CATEGORY?Set.of("name","status","sort","parentId","icon","showOnFront"):Set.of("name","status","sort");
        f.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new CoolException("此模块不支持字段："+k,400);});
        Value base=before==null?new Value(null,null,1,0,null,null,scope()==Scope.CATEGORY?1:null):before;
        Value after=new Value(base.id(),str(f,"name",base.name()),integer(f,"status",base.status()),integer(f,"sort",base.sort()),
            nullableId(f,"parentId",base.parentId()),str(f,"icon",base.icon()),integer(f,"showOnFront",base.showOnFront()));
        if(after.name()==null || after.name().isBlank() || after.name().length()>50)throw new CoolException("请提供1至50字的名称",400);
        if(after.sort()==null || after.sort()<0 || after.status()==null || !Set.of(0,1).contains(after.status()))
            throw new CoolException("排序必须非负，状态只能为0或1",400);
        return new Result(scope(),"PREVIEW","尚未保存；请核对字段，新建默认启用、排序0",new Change(step.action(),before,after));
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public Result execute(Actor actor,Step step,Result expected) {
        Result latest=preview(actor,step);
        if(!Objects.equals(latest.data(),expected.data()))throw new CoolException("目标资料已变化，请重新预览",409);
        Change change=(Change)latest.data(); Long id=step.id();
        if(scope()==Scope.CATEGORY) {
            if(step.action()==Action.DELETE)categories.delete(id);
            else {
                Value v=change.after();var r=new MerchantCategoryEntity();r.setId(v.id());r.setName(v.name());
                r.setStatus(v.status());r.setSort(v.sort());r.setParentId(v.parentId());r.setIcon(v.icon());r.setShowOnFront(v.showOnFront());
                if(step.action()==Action.CREATE)id=categories.add(r);else {
                    categories.update(r);
                    // The original mapper ignores nulls. Apply explicitly cleared optional fields
                    // only after the business service validates the full resulting category.
                    if(v.parentId()==null)UpdateChain.of(MerchantCategoryEntity.class)
                        .set(MerchantCategoryEntity::getParentId,null).eq(MerchantCategoryEntity::getId,r.getId()).update();
                    if(v.icon()==null)UpdateChain.of(MerchantCategoryEntity.class)
                        .set(MerchantCategoryEntity::getIcon,null).eq(MerchantCategoryEntity::getId,r.getId()).update();
                }
            }
        } else {
            if(step.action()==Action.DELETE)types.delete(id);
            else {
                Value v=change.after();var r=new MerchantStoreCategoryEntity();r.setId(v.id());r.setName(v.name());r.setStatus(v.status());r.setSort(v.sort());
                if(step.action()==Action.CREATE)id=types.add(r);else types.update(r);
            }
        }
        return new Result(scope(),"COMPLETED","业务服务已成功执行",new Change(step.action(),change.before(),step.action()==Action.DELETE?null:current(id)));
    }
    private static String str(JsonNode f,String k,String old) {
        if(!f.has(k))return old;if(f.get(k).isNull())return null;
        if(!f.get(k).isTextual())throw new CoolException("字段类型不正确："+k,400);return f.get(k).asText();
    }
    private static Integer integer(JsonNode f,String k,Integer old) {
        if(!f.has(k))return old;if(!f.get(k).canConvertToInt() || !f.get(k).isIntegralNumber())throw new CoolException("字段必须为整数："+k,400);
        return f.get(k).intValue();
    }
    private static Long nullableId(JsonNode f,String k,Long old) {
        if(!f.has(k))return old;if(f.get(k).isNull())return null;
        if(!f.get(k).isIntegralNumber() || !f.get(k).canConvertToLong() || f.get(k).longValue()<=0)throw new CoolException("无效关联ID",400);
        return f.get(k).longValue();
    }
}
