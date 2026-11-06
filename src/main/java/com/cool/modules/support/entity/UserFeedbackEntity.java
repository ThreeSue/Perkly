package com.cool.modules.support.entity;
import com.cool.core.base.BaseEntity;
import com.cool.core.annotation.ColumnDefine;
import com.mybatisflex.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存用户反馈图片与平台单次处理回复
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:37:33
 */
@Getter @Setter @Table("support_feedback")
@org.dromara.autotable.annotation.TableIndex(name="uk_support_feedback_request",type=org.dromara.autotable.annotation.enums.IndexTypeEnum.UNIQUE,fields={"customerId","requestId"})
public class UserFeedbackEntity extends BaseEntity<UserFeedbackEntity> {
    @org.dromara.autotable.annotation.Ignore
    @Column(ignore=true) private String customerName;
    @ColumnDefine(notNull=true,comment="反馈用户") private Long customerId;
    @ColumnDefine(length=64,notNull=true,comment="幂等请求标识") private String requestId;
    @ColumnDefine(type="text",notNull=true,comment="反馈内容") private String content;
    @ColumnDefine(type="json",comment="图片URL列表")
    @Column(typeHandler=com.cool.core.mybatis.handler.Fastjson2TypeHandler.class)
    private List<String> images;
    @ColumnDefine(length=20,notNull=true,comment="PENDING或REPLIED") private String status;
    @ColumnDefine(type="text",comment="平台回复") private String reply;
    @ColumnDefine(comment="回复运营账号") private Long repliedBy;
    @ColumnDefine(comment="回复时间") private Date repliedAt;
}
