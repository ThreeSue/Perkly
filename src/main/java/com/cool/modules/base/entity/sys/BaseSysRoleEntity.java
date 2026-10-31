package com.cool.modules.base.entity.sys;

import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import com.cool.core.mybatis.handler.Fastjson2TypeHandler;
import com.cool.core.annotation.ColumnDefine;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.dromara.autotable.annotation.Index;
import org.dromara.autotable.annotation.enums.IndexTypeEnum;

@Getter
@Setter
@Table(value = "base_sys_role", comment = "系统角色表")
public class BaseSysRoleEntity extends BaseEntity<BaseSysRoleEntity> {
    @ColumnDefine(comment = "角色适用管理空间，空值为原平台角色", length = 20)
    private String accountType;
    @ColumnDefine(comment = "自定义角色所属商家")
    private Long merchantId;
    @ColumnDefine(comment = "本店自定义角色归属门店")
    private Long storeId;
    @ColumnDefine(comment = "内置共享角色", defaultValue = "0")
    private Boolean builtin;
    @ColumnDefine(comment = "角色状态：1启用0停用", defaultValue = "1")
    private Integer status;
    @ColumnDefine(comment = "可向下分配的菜单操作", type = "json")
    @Column(typeHandler = Fastjson2TypeHandler.class)
    private List<Long> grantMenuIdList;


    @Index
    @ColumnDefine(comment = "用户ID", notNull = true, type = "bigint")
    private Long userId;

    @ColumnDefine(comment = "名称", notNull = true)
    private String name;

    @Index(type = IndexTypeEnum.UNIQUE)
    @ColumnDefine(comment = "角色标签", notNull = true)
    private String label;

    @ColumnDefine(comment = "备注")
    private String remark;

    @ColumnDefine(comment = "数据权限是否关联上下级", defaultValue = "1")
    private Integer relevance;

    @ColumnDefine(comment = "菜单权限", type = "json")
    @Column(typeHandler = Fastjson2TypeHandler.class)
    private List<Long> menuIdList;

    @ColumnDefine(comment = "部门权限", type = "json")
    @Column(typeHandler = Fastjson2TypeHandler.class)
    private List<Long> departmentIdList;
}
