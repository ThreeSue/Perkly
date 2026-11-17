package com.cool.modules.searchrisk;

import com.cool.core.base.BaseEntity;
import com.cool.modules.approval.entity.MerchantApplicationEntity;
import com.cool.modules.approval.mapper.MerchantApplicationMapper;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.coupon.mapper.CouponTemplateRelationsMapper;
import com.cool.modules.searchrisk.mapper.PlatformWarningMapper;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.support.entity.StoreChatMessageEntity;
import com.cool.modules.support.entity.UserFeedbackEntity;
import com.cool.modules.support.mapper.StoreChatMessageMapper;
import com.cool.modules.support.mapper.UserFeedbackMapper;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.workspace.entity.WorkspaceNoticeEntity;
import com.cool.modules.workspace.mapper.WorkspaceNoticeMapper;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.util.LambdaGetter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按业务对象分页读取原始内容和可核实的提交人
 * @Version: v1.1
 * @Date: 2026-11-14
 * @Time: 23:24:30
 */
@Service
@RequiredArgsConstructor
public class RiskOriginalContent {
    public static final Set<String> SOURCES = Set.of(
        "PROFILE", "WORKSPACE_PROFILE", "FEEDBACK", "CHAT", "MERCHANT", "STORE", "COUPON", "NOTICE",
        "MERCHANT_APPLICATION", "PLATFORM_WARNING");

    private final UserInfoMapper users;
    private final BaseSysUserMapper workspaceUsers;
    private final UserFeedbackMapper feedback;
    private final StoreChatMessageMapper messages;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;
    private final CouponTemplateMapper coupons;
    private final WorkspaceNoticeMapper notices;
    private final MerchantApplicationMapper applications;
    private final CouponTemplateRelationsMapper couponItems;
    private final PlatformWarningMapper warnings;
    private final RiskContentAuthors authors;

    public record Field(String scene, String businessType, String value, SearchRiskService.Subject author) {}
    public record Original(Long id, List<Field> fields) {}

    public List<Original> page(String source, long afterId, int limit) {
        return switch (source) {
            case "PROFILE" -> page(users, UserInfoEntity::getId, afterId, limit, this::profile);
            case "WORKSPACE_PROFILE" -> page(workspaceUsers, BaseSysUserEntity::getId, afterId, limit, this::workspaceProfile);
            case "FEEDBACK" -> page(feedback, UserFeedbackEntity::getId, afterId, limit, this::feedback);
            case "CHAT" -> page(messages, StoreChatMessageEntity::getId, afterId, limit, this::message);
            case "MERCHANT" -> page(merchants, MerchantInfoEntity::getId, afterId, limit, this::merchant);
            case "STORE" -> page(stores, MerchantStoreEntity::getId, afterId, limit, this::store);
            case "COUPON" -> page(coupons, CouponTemplateEntity::getId, afterId, limit, this::coupon);
            case "NOTICE" -> page(notices, WorkspaceNoticeEntity::getId, afterId, limit, this::notice);
            case "MERCHANT_APPLICATION" -> applications.selectListByQuery(QueryWrapper.create()
                .gt(MerchantApplicationEntity::getId, afterId).orderBy(MerchantApplicationEntity::getId, true)
                .limit(limit)).stream().map(this::application).toList();
            case "PLATFORM_WARNING" -> page(warnings, PlatformWarning::getId, afterId, limit, this::warning);
            default -> throw new IllegalArgumentException("Invalid original content source");
        };
    }

    public Field field(String businessType, Long id, String recordedText) {
        if (businessType == null || id == null) return null;
        String fieldType = RiskOrigin.baseField(businessType);
        Original original = switch (fieldType) {
            case "USER_NICKNAME" -> find(users, id, this::profile);
            case "WORKSPACE_NICKNAME" -> find(workspaceUsers, id, this::workspaceProfile);
            case "FEEDBACK", "FEEDBACK_REPLY" -> find(feedback, id, this::feedback);
            case "CHAT_MESSAGE" -> find(messages, id, this::message);
            case "MERCHANT_NAME", "MERCHANT_INTRODUCTION", "MERCHANT_ADDRESS" ->
                find(merchants, id, this::merchant);
            case "STORE_NAME", "STORE_HOURS", "STORE_ADDRESS" ->
                find(stores, id, this::store);
            case "COUPON_NAME", "COUPON_DESCRIPTION", "COUPON_ITEM_NAME", "COUPON_ITEM_DESCRIPTION" -> find(coupons, id, this::coupon);
            case "NOTICE_TITLE", "NOTICE_CONTENT" -> find(notices, id, this::notice);
            case "MERCHANT_APPLICATION_NAME", "MERCHANT_APPLICATION_INTRODUCTION" -> {
                var row = applications.selectOneById(id);
                yield row == null ? null : application(row);
            }
            case "PLATFORM_WARNING_TITLE", "PLATFORM_WARNING_CONTENT" -> find(warnings, id, this::warning);
            default -> null;
        };
        if (original == null) return null;
        return original.fields().stream().filter(field -> businessType.equals(field.businessType()))
            .filter(field -> recordedText == null || RiskHistoryService.sameOriginal(recordedText, field.value()))
            .findFirst().orElse(null);
    }

    private <T extends BaseEntity<T>> List<Original> page(BaseMapper<T> mapper, LambdaGetter<T> id, long afterId,
                                                         int limit, Function<T, Original> convert) {
        return mapper.selectListByQuery(QueryWrapper.create().gt(id, afterId).orderBy(id, true).limit(limit))
            .stream().map(convert).toList();
    }

    private <T extends BaseEntity<T>> Original find(BaseMapper<T> mapper, Long value, Function<T, Original> convert) {
        var row = mapper.selectOneById(value);
        return row == null ? null : convert.apply(row);
    }

    private Original profile(UserInfoEntity row) {
        return withAuthors(row.getId(),List.of(new Field("PROFILE","USER_NICKNAME",row.getNickName(),null)));
    }

    private Original workspaceProfile(BaseSysUserEntity row) {
        return withAuthors(row.getId(),List.of(new Field("PROFILE","WORKSPACE_NICKNAME",row.getNickName(),null)));
    }

    private Original feedback(UserFeedbackEntity row) {
        return original(row.getId(),
            new Field("FEEDBACK", "FEEDBACK", row.getContent(), new SearchRiskService.Subject("APP", row.getCustomerId())),
            new Field("FEEDBACK", "FEEDBACK_REPLY", row.getReply(), workspaceAuthor(row.getRepliedBy())));
    }

    private Original message(StoreChatMessageEntity row) {
        if ("SYSTEM".equals(row.getSenderKind())) return original(row.getId());
        var author = switch (String.valueOf(row.getSenderKind())) {
            case "APP" -> new SearchRiskService.Subject("APP", row.getSenderId());
            case "WORKSPACE" -> workspaceAuthor(row.getSenderId());
            case "AI" -> new SearchRiskService.Subject("SYSTEM", null);
            default -> null;
        };
        return original(row.getId(), new Field("AI".equals(row.getSenderKind()) ? "AI_OUTPUT" : "CHAT",
            "CHAT_MESSAGE", row.getContent(), author));
    }

    private Original merchant(MerchantInfoEntity row) {
        return withAuthors(row.getId(), List.of(new Field("MERCHANT", "MERCHANT_NAME", row.getName(), null),
            new Field("MERCHANT", "MERCHANT_INTRODUCTION", row.getIntroduction(), null),
            new Field("MERCHANT", "MERCHANT_ADDRESS", row.getAddress(), null)));
    }

    private Original store(MerchantStoreEntity row) {
        return withAuthors(row.getId(),List.of(new Field("STORE","STORE_NAME",row.getName(),null),
            new Field("STORE","STORE_HOURS",row.getBusinessHours(),null),
            new Field("STORE","STORE_ADDRESS",row.getAddress(),null)));
    }

    private Original coupon(CouponTemplateEntity row) {
        var fields = new java.util.ArrayList<Field>();
        fields.add(new Field("COUPON", "COUPON_NAME", row.getName(), null));
        fields.add(new Field("COUPON", "COUPON_DESCRIPTION", row.getDescription(), null));
        for (var item : couponItems.contentRows(row.getId())) {
            if (!(item.get("product_id") instanceof Number product)) continue;
            fields.add(new Field("COUPON", "COUPON_ITEM_NAME:" + product.longValue(), (String) item.get("name"), null));
            fields.add(new Field("COUPON", "COUPON_ITEM_DESCRIPTION:" + product.longValue(), (String) item.get("item_description"), null));
        }
        return withAuthors(row.getId(), fields);
    }

    private Original application(MerchantApplicationEntity row) {
        String introduction = null;
        if (cn.hutool.json.JSONUtil.isTypeJSONObject(row.getSnapshot())) {
            introduction = cn.hutool.json.JSONUtil.parseObj(row.getSnapshot()).getStr("introduction");
        }
        return original(row.getId(),
            new Field("MERCHANT", "MERCHANT_APPLICATION_NAME", row.getMerchantName(), new SearchRiskService.Subject("GUEST", null)),
            new Field("MERCHANT", "MERCHANT_APPLICATION_INTRODUCTION", introduction, new SearchRiskService.Subject("GUEST", null)));
    }

    private Original warning(PlatformWarning row) {
        if (row.getWorkspaceNoticeId() != null) return original(row.getId());
        var author = workspaceAuthor(row.getSenderId());
        return original(row.getId(), new Field("NOTICE", "PLATFORM_WARNING_TITLE", row.getTitle(), author),
            new Field("NOTICE", "PLATFORM_WARNING_CONTENT", row.getContent(), author));
    }

    private Original notice(WorkspaceNoticeEntity row) {
        var author = workspaceAuthor(row.getSenderId());
        return original(row.getId(), new Field("NOTICE", "NOTICE_TITLE", row.getTitle(), author),
            new Field("NOTICE", "NOTICE_CONTENT", row.getContent(), author));
    }

    private static SearchRiskService.Subject workspaceAuthor(Long id) {
        return id == null ? null : id == 0 ? new SearchRiskService.Subject("SYSTEM", null)
            : new SearchRiskService.Subject("WORKSPACE", id);
    }

    private static Original original(Long id, Field... fields) {
        return new Original(id, List.of(fields));
    }

    private Original withAuthors(Long id, List<Field> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        for (var field : fields) {
            if (field.value() != null && !field.value().isBlank()) values.put(field.businessType(), field.value());
        }
        var matched = authors.find(id,values);
        return new Original(id,fields.stream().map(field -> new Field(field.scene(),field.businessType(),
            field.value(),matched.get(field.businessType()))).toList());
    }
}
