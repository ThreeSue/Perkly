package com.cool.modules.merchant.service;

import com.cool.modules.merchant.controller.admin.params.BackendAccountParam.*;
import com.cool.modules.merchant.resp.BackendAccountResp.*;
import java.util.List;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 定义平台统一查询商家及门店账号和维护商家管理员的服务
 * @Version: v1.1
 * @Date: 2026-10-22
 * @Time: 22:10:23
 */
public interface BackendAccountService {
    List<Group> groups();
    AccountPage page(PageInput input);
    void saveOwner(OwnerInput input);
    void resetOwner(Long id);
}
