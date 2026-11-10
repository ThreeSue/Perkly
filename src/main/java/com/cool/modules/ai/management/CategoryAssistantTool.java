package com.cool.modules.ai.management;
import com.cool.modules.merchant.service.*;
import org.springframework.stereotype.Service;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 注册商家分类助手工具
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@Service
public class CategoryAssistantTool extends CatalogAssistantTool {
    public CategoryAssistantTool(MerchantCategoryService categories,MerchantStoreCategoryService types,ManagementAssistantAccess access) {super(categories,types,access);}
    @Override public ManagementAssistantModels.Scope scope(){return ManagementAssistantModels.Scope.CATEGORY;}
}
