package com.cool.modules.workspace.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存商家岗位账号可查看的指定门店清单
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:22:00
 */
@Getter @Setter @Table("workspace_user_scope")
@org.dromara.autotable.annotation.TableIndex(name="uk_workspace_user_store", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"userId","storeId"})
public class WorkspaceUserScopeEntity extends BaseEntity<WorkspaceUserScopeEntity> {
    @ColumnDefine(comment="商家账号ID") private Long userId;
    @ColumnDefine(comment="授权门店ID") private Long storeId;
}
