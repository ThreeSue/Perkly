package com.cool.modules.searchrisk;

import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.support.entity.StoreChatEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 记录风险检测当时的会话、双方和人工接待快照
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 10:09:30
 */
@Component
@RequiredArgsConstructor
public class RiskChatContext {
    private final UserInfoMapper customers;
    private final BaseSysUserMapper users;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;

    public SearchRisk.ChatContext snapshot(StoreChatEntity chat) {
        return describe(chat, true, chat.getRound());
    }

    public SearchRisk.ChatContext historical(StoreChatEntity chat, Integer savedRound) {
        return describe(chat, false, savedRound);
    }

    private SearchRisk.ChatContext describe(StoreChatEntity chat, boolean snapshot, Integer round) {
        var customer = customers.selectOneById(chat.getCustomerId());
        var merchant = merchants.selectOneById(chat.getMerchantId());
        var store = stores.selectOneById(chat.getStoreId());
        Long assigneeId = snapshot ? chat.getAssigneeId() : null;
        var assignee = assigneeId == null ? null : users.selectOneById(assigneeId);
        String assigneeName = assignee == null ? null : assignee.getName();
        if (assignee != null && (assigneeName == null || assigneeName.isBlank())) assigneeName = assignee.getNickName();
        return new SearchRisk.ChatContext(chat.getId(), chat.getStoreId(), store == null ? null : store.getName(),
            chat.getMerchantId(), merchant == null ? null : merchant.getName(), chat.getCustomerId(),
            customer == null ? null : customer.getNickName(), snapshot ? chat.getState() : null,
            assigneeId, assigneeName, round, snapshot ? chat.getHumanRequested() : null);
    }
}
