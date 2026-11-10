package com.cool.modules.ai.management;

import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 声明有限业务工具的查询预览与执行边界
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
public interface ManagementAssistantTool {
    Scope scope();
    Result query(Actor actor, Step step);
    default Result preview(Actor actor, Step step) {
        throw new com.cool.core.exception.CoolException("此模块仅支持数据询问",403);
    }
    default Result execute(Actor actor, Step step, Result expected) {
        throw new com.cool.core.exception.CoolException("此模块不支持写入",403);
    }
}
