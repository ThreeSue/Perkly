package com.cool.modules.base.entity.sys;

import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import com.cool.core.annotation.ColumnDefine;
import lombok.Getter;
import lombok.Setter;
import org.dromara.autotable.annotation.Index;

@Getter
@Setter
@Table(value = "base_sys_user_role", comment = "系统用户角色表")
@org.dromara.autotable.annotation.TableIndex(name="uk_workspace_single_user_role", type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE, fields={"userId"})
public class BaseSysUserRoleEntity extends BaseEntity<BaseSysUserRoleEntity> {
    @Index(type = org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE)
    @ColumnDefine(comment = "用户ID", type = "bigint")
    private Long userId;

    @Index
    @ColumnDefine(comment = "角色ID", type = "bigint")
    private Long roleId;
}
