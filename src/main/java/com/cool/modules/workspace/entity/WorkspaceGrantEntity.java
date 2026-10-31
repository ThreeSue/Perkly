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
 * @Description: 保存商家门店功能上限与单账号个人增减权限
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:22:00
 */
@Getter @Setter @Table("workspace_grant")
@org.dromara.autotable.annotation.TableIndex(name="uk_workspace_grant_subject_menu", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"subjectType","subjectId","menuId"})
public class WorkspaceGrantEntity extends BaseEntity<WorkspaceGrantEntity> {
    @ColumnDefine(comment="授权主体MERCHANT、STORE或USER",length=20) private String subjectType;
    @ColumnDefine(comment="授权主体ID") private Long subjectId;
    @ColumnDefine(comment="菜单操作ID，0表示已显式配置范围") private Long menuId;
    @ColumnDefine(comment="授予或拒绝") private Boolean allowed;
}
