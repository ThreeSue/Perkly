package com.cool.modules.searchrisk;

import com.cool.core.annotation.ColumnDefine;
import com.cool.core.base.BaseEntity;
import com.mybatisflex.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存历史风险整理预览和对应应用回执
 * @Version: v1.1
 * @Date: 2026-11-14
 * @Time: 23:26:40
 */
@Getter
@Setter
@Table("risk_history_preview")
public class RiskHistoryPreview extends BaseEntity<RiskHistoryPreview> {
    private Long operatorId;
    private Date expiresAt;
    @ColumnDefine(type = "mediumtext")
    private String snapshot;
    @ColumnDefine(type = "mediumtext")
    private String receipts;
}
