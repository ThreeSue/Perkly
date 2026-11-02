package com.cool.modules.workspace.service;

import java.util.Set;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 判定通知发送人对目标账号的层级与门店范围
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 00:19:10
 */
public final class NoticeRecipientPolicy {
    private NoticeRecipientPolicy() {}

    public record Sender(Long id, String type, Long merchantId, Long storeId,
                         Set<Long> permittedStoreIds, boolean sendPermission, boolean ordinaryStaff) {}
    public record Recipient(Long id, String type, Long merchantId, Long storeId, boolean enabled, boolean ordinaryStaff) {}

    /** 接入接口前，调用方应从可信身份与权限服务取得全部参数。 */
    public static boolean canSend(Sender sender, Recipient recipient) {
        if (sender == null || recipient == null || sender.id() == null || recipient.id() == null
                || sender.id().equals(recipient.id()) || !sender.sendPermission() || !recipient.enabled()) return false;
        if ("PLATFORM".equals(sender.type())) return "MERCHANT".equals(recipient.type()) || "STORE".equals(recipient.type());
        if (sender.merchantId() == null || !sender.merchantId().equals(recipient.merchantId())
                || !"STORE".equals(recipient.type()) || recipient.storeId() == null) return false;
        if ("MERCHANT".equals(sender.type()))
            return sender.permittedStoreIds() != null && sender.permittedStoreIds().contains(recipient.storeId());
        if ("STORE".equals(sender.type()))
            return !sender.ordinaryStaff() && recipient.ordinaryStaff() && sender.storeId() != null && sender.storeId().equals(recipient.storeId());
        return false;
    }
}
