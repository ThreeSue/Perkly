package com.cool.modules.merchant.service;
import java.util.Set;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保留旧门店0与1编码并区分停业和关闭的业务状态
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:40:00
 */
public final class StoreStatus {
    private StoreStatus() {}
    public static final int DISABLED=0,OPEN=1,SUSPENDED=2,CLOSED=3;
    public static boolean valid(Integer value){return value!=null&&Set.of(DISABLED,OPEN,SUSPENDED,CLOSED).contains(value);}
    public static String text(Integer value){return value==null?"状态未知":switch(value){case OPEN->"营业";case SUSPENDED->"停业";case DISABLED->"禁用";case CLOSED->"关闭";default->"状态未知";};}
}
