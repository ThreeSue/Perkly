package com.cool.modules.workspace.controller;
import com.cool.core.request.R;
import com.cool.modules.workspace.service.*;
import com.cool.modules.workspace.model.WorkspaceModels.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 三端共享的账号岗位授权和商家设置接口
 * @Version: v1.1
 * @Date: 2026-10-30
 * @Time: 00:30:00
 */
@RestController @RequestMapping({"/admin/workspace","/merchant/governance"}) @RequiredArgsConstructor
public class WorkspaceGovernanceController {
    private final WorkspaceManagementService management;
    private final WorkspaceAuthorization authorization;
    private final HttpServletRequest request;
    private Long actor(){if(request.getRequestURI().startsWith("/admin/"))return null;Object id=request.getAttribute("merchantUserId");if(!(id instanceof Long))throw new com.cool.core.exception.CoolException("请先登录",401);return (Long)id;}
    @GetMapping("/me") public R<?> me(){Long id=actor();if(id==null)throw new com.cool.core.exception.CoolException("此接口用于商家门店身份",403);return R.ok(authorization.context(id));}
    @PostMapping("/personal") public R<?> personal(@RequestBody PersonalInput p){management.personal(actor(),p);return R.ok();}
    @GetMapping("/permmenu") public R<?> menu(){Long id=actor();if(id==null)throw new com.cool.core.exception.CoolException("请使用运营菜单接口",403);return R.ok(authorization.permmenu(id));}
    @GetMapping("/merchants") public R<?> merchants(){return R.ok(management.merchants(actor()));}
    @GetMapping("/stores") public R<?> stores(@RequestParam(required=false)Long merchantId){return R.ok(management.stores(actor(),merchantId));}
    @GetMapping("/catalog") public R<?> catalog(){return R.ok(management.catalog(actor()));}
    @PostMapping("/roles/list") public R<?> roles(@RequestBody Filter p){return R.ok(management.roleList(actor(),p));}
    @PostMapping("/roles/save") public R<?> roleSave(@RequestBody RoleInput p){return R.ok(management.saveRole(actor(),p));}
    @PostMapping("/roles/delete") public R<?> roleDelete(@RequestBody IdInput p){management.deleteRole(actor(),p.id());return R.ok();}
    @GetMapping("/roles/options") public R<?> roleOptions(@RequestParam(required=false)Long merchantId,@RequestParam String accountType,@RequestParam(required=false)Long storeId){return R.ok(management.assignableRoles(actor(),merchantId,accountType,storeId));}
    @PostMapping("/accounts/list") public R<?> accounts(@RequestBody Filter p){return R.ok(management.accounts(actor(),p));}
    @PostMapping("/accounts/save") public R<?> accountSave(@RequestBody AccountInput p){return R.ok(management.saveAccount(actor(),p));}
    @PostMapping("/accounts/preview") public R<?> accountPreview(@RequestBody AccountInput p){return R.ok(management.previewAccount(actor(),p));}
    @PostMapping("/accounts/status") public R<?> accountStatus(@RequestBody StateInput p){management.accountState(actor(),p);return R.ok();}
    @GetMapping("/grants") public R<?> grants(@RequestParam String subjectType,@RequestParam Long subjectId){return R.ok(management.grant(actor(),subjectType,subjectId));}
    @PostMapping("/grants/save") public R<?> grantSave(@RequestBody GrantInput p){management.saveGrant(actor(),p);return R.ok();}
    @GetMapping("/settings") public R<?> settings(@RequestParam(required=false)Long merchantId){return R.ok(management.settings(actor(),merchantId));}
    @PostMapping("/settings") public R<?> saveSettings(@RequestParam(required=false)Long merchantId,@RequestBody MerchantSettings p){management.saveSettings(actor(),merchantId,p);return R.ok();}
}
