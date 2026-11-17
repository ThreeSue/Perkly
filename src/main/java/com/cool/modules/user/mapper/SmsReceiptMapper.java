package com.cool.modules.user.mapper;

import com.cool.modules.user.sms.SmsReceipt;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 读写短信送达回执
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 16:50:00
 */
@Mapper
public interface SmsReceiptMapper extends BaseMapper<SmsReceipt> {}
