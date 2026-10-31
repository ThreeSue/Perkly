package com.cool.modules.coupon.controller.admin.req;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.cool.core.exception.CoolException;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 接收购券订单监管分页筛选及创建时间范围
 * @Version: v1.1
 * @Date: 2026-10-28
 * @Time: 11:48:23
 */

@Getter @Setter
public class PurchaseOrderQuery {
    private Integer page=1;
    private Integer size=20;
    private Long merchantId;
    private String orderNo;
    private String status;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") private Date startTime;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss",timezone="GMT+8") private Date endTime;
    public void validate() {
        if(page==null || page<1 || page>100000 || size==null || size<1 || size>100) throw new CoolException("分页参数不正确");
        if(merchantId!=null && merchantId<1) throw new CoolException("商家筛选无效");
        if(orderNo!=null){orderNo=orderNo.trim();if(orderNo.length()>40)throw new CoolException("订单号不能超过40字");}
        if(status!=null && !java.util.Set.of("PENDING","PAID","CANCELLED","FAILED","EXPIRED").contains(status))throw new CoolException("订单状态无效");
        if(startTime!=null && endTime!=null && startTime.after(endTime))throw new CoolException("开始时间不能晚于结束时间");
    }
}
