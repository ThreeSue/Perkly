package com.cool.modules.coupon;

import com.cool.modules.coupon.controller.admin.params.CouponContentParam;
import com.cool.modules.coupon.entity.CouponTemplateEntity;
import com.cool.modules.coupon.mapper.CouponTemplateMapper;
import com.cool.modules.coupon.mapper.CouponTemplateRelationsMapper;
import com.cool.modules.coupon.service.CouponPublicationService;
import com.cool.modules.coupon.service.CouponTemplateService;
import com.mybatisflex.core.row.Row;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按明确运行开关随机配置现有演示券的内容审核及上架状态
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 17:22:52
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "sms-auth.access-key-id=demo-unused", "sms-auth.access-key-secret=demo-unused"
})
@ActiveProfiles("local")
@Transactional
@Commit
@EnabledIfSystemProperty(named = "coupon.demo.apply", matches = "true")
class CouponDemoSetup {
    @Autowired CouponTemplateService templates;
    @Autowired CouponTemplateMapper mapper;
    @Autowired CouponTemplateRelationsMapper relations;
    @Autowired CouponPublicationService publication;

    @Test void applyRandomDistribution() throws Exception {
        List<CouponTemplateEntity> rows = new ArrayList<>(mapper.selectAll());
        if (rows.size() != 24 || rows.stream().anyMatch(e -> !e.getName().startsWith("演示-"))) {
            throw new IllegalStateException("数据已变化，请重新核对数量和演示范围后执行");
        }
        Collections.shuffle(rows, new Random(202610201705L));
        StringBuilder report = new StringBuilder("# 优惠券随机演示分配\n\n24张券中19张审核通过并上架，另5张分散状态。随机种子202610201705。\n\n| 券名称 | 状态 |\n|---|---|\n");
        for (int i = 0; i < rows.size(); i++) {
            Long id = rows.get(i).getId();
            relations.lockTemplate(id);
            CouponTemplateEntity e = (CouponTemplateEntity)templates.info(id);
            // 仅此次显式演示整理重置当前流转状态，后续提交审核和上下架均走正式服务。
            relations.savePublication(e.getMerchantId(), id, Row.of("audit_status", 0).set("publish_status", 0));
            boolean draft = i == 19;
            e.setStatus(draft ? 0 : 1);
            if (Set.of("PACKAGE", "EXPERIENCE", "EXCHANGE").contains(e.getType()) && e.getContentItems().isEmpty()) {
                e.setContentItems(contents(e.getName()));
                if (e.getDescription() != null) e.setDescription(e.getDescription().replace("草稿：", "").replaceAll("；待配置[^。]*。?", "。"));
            }
            templates.update(e);
            e = (CouponTemplateEntity)templates.info(id);
            int version = e.getRuleVersion();
            String state;
            if (draft) state = "草稿／未提交";
            else {
                publication.submitAudit(id, version);
                if (i == 20) state = "待审核";
                else {
                    publication.audit(id, version, i != 21);
                    if (i == 21) state = "已驳回／未上架";
                    else if (i == 22) state = "审核通过／待上架";
                    else {
                        publication.publish(id, version);
                        if (i == 23) { publication.unpublish(id, version); state = "审核通过／已下架"; }
                        else state = "审核通过／已上架";
                    }
                }
            }
            report.append("| ").append(e.getName()).append(" | ").append(state).append(" |\n");
        }
        Files.writeString(Path.of("../testing/data/coupon-random-publication-20261020.md"), report, StandardCharsets.UTF_8);
    }

    private List<CouponContentParam> contents(String name) {
        if (name.contains("便当")) return List.of(item("当日主食", "GOODS", 1, "一份"), item("时蔬配菜", "GOODS", 1, "一份"), item("套餐饮品", "GOODS", 1, "一杯"));
        if (name.contains("火锅")) return List.of(item("双人锅底", "GOODS", 1, "一锅"), item("精选肥牛", "GOODS", 1, "150克／份"), item("手打虾滑", "GOODS", 1, "100克／份"), item("时蔬拼盘", "GOODS", 1, "一份"));
        if (name.contains("家庭")) return List.of(item("一大一小亲子活动", "SERVICE", 1, "一组家庭参加一次，需预约"));
        if (name.contains("下午茶")) return List.of(item("中杯咖啡", "GOODS", 2, "每杯约300毫升"), item("当日甜品", "GOODS", 1, "一份"));
        if (name.contains("健身") || name.contains("器械")) return List.of(item("单次健身体验", "EXPERIENCE", 1, "一次入场，具体开放时间请咨询门店"));
        if (name.contains("亲子活动")) return List.of(item("亲子活动体验", "EXPERIENCE", 1, "参加一次活动，需预约"));
        if (name.contains("美甲")) return List.of(item("基础美甲体验", "SERVICE", 1, "基础款一次，不含延长甲"));
        if (name.contains("洗鞋")) return List.of(item("标准洗鞋服务", "SERVICE", 1, "普通运动鞋一双，特殊材质请咨询门店"));
        if (name.contains("宠物")) return List.of(item("宠物基础洗护", "SERVICE", 1, "一次基础洗护，适用体型请预约确认"));
        if (name.contains("吐司")) return List.of(item("原味吐司", "GOODS", 1, "一份，当日供应"));
        if (name.contains("糖水")) return List.of(item("招牌糖水", "GOODS", 1, "一份，当日可选款式以门店为准"));
        throw new IllegalStateException("没有匹配的演示内容：" + name);
    }

    private CouponContentParam item(String name, String type, int quantity, String note) {
        CouponContentParam item = new CouponContentParam();
        item.setName(name); item.setType(type); item.setQuantity(quantity); item.setItemDescription(note);
        return item;
    }
}
