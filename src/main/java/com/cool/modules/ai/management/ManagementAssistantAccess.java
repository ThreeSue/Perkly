package com.cool.modules.ai.management;

import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.cool.modules.ai.management.ManagementAssistantModels.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按当前管理身份和实际业务权限授权助手工具
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 09:18:00
 */
@Service
@RequiredArgsConstructor
public class ManagementAssistantAccess {
    private final SupportAccess support;
    private final WorkspaceAuthorization workspace;
    public void identity(Actor actor) {
        if(actor == null || actor.id() == null) throw new CoolException("请先登录",401);
        if(actor.platform()) {
            if(CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN ||
                !actor.id().equals(CoolSecurityUtil.getCurrentUserId()))
                throw new CoolException("请使用当前运营身份",403);
        } else workspace.context(actor.id());
    }
    public void require(Actor actor,String platformPermission,String workspacePermission) {
        identity(actor);
        if(actor.platform()) support.platform(platformPermission);
        else {
            if(workspacePermission==null) throw new CoolException("此能力仅向有权限的运营人员开放",403);
            workspace.require(actor.id(),workspacePermission);
        }
    }
    public boolean permitted(Actor actor,String platformPermission,String workspacePermission) {
        try {require(actor,platformPermission,workspacePermission);return true;}
        catch(CoolException denied){return false;}
    }
    public void approvalRead(Actor actor) {
        if(actor.platform() && permitted(actor,"approval:center:review",null))return;
        require(actor,"approval:center:page","coupon:view");
    }
    public String accountType(Actor actor) {
        identity(actor);
        return actor.platform()?"PLATFORM":workspace.context(actor.id()).accountType();
    }
    public static void scope(Scope entry, Scope tool) {
        if(entry==null || tool==null || entry!=Scope.GLOBAL && entry!=tool)
            throw new CoolException("此侧栏仅服务当前模块，请前往AI工作台继续",403);
    }
}
