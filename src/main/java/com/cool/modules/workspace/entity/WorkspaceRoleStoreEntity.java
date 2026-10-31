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
 * @Description: 限制门店岗位可以分配到的门店范围
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:22:00
 */
@Getter @Setter @Table("workspace_role_store")
@org.dromara.autotable.annotation.TableIndex(name="uk_workspace_role_store", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"roleId","storeId"})
public class WorkspaceRoleStoreEntity extends BaseEntity<WorkspaceRoleStoreEntity> {
    @ColumnDefine(comment="岗位ID") private Long roleId;
    @ColumnDefine(comment="适用门店ID") private Long storeId;
}
