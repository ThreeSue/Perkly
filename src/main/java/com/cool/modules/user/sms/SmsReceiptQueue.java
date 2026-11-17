package com.cool.modules.user.sms;

import java.util.List;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 隔离短信回执队列读取与确认操作以便验证失败重投
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 16:53:00
 */
public interface SmsReceiptQueue extends AutoCloseable {
    record Message(String handle, String body) {}
    List<Message> receive() throws Exception;
    void acknowledge(String handle) throws Exception;
    void close();
}
