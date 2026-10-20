package com.cool.modules.user.controller.admin;

import static com.cool.modules.user.entity.table.UserInfoEntityTableDef.USER_INFO_ENTITY;

import cn.hutool.json.JSONObject;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.base.BaseController;
import com.cool.core.request.CrudOption;
import com.cool.core.request.R;
import com.cool.modules.user.service.AdminUserAccountQueryService;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.service.UserInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "用户信息", description = "用户信息")
@CoolRestController(api = {"add", "delete", "update", "page", "list", "info"})
@RequiredArgsConstructor
public class AdminUserInfoController extends BaseController<UserInfoService, UserInfoEntity> {
    private final AdminUserAccountQueryService accountQuery;

    @Override
    @PostMapping("/page")
    protected R page(@RequestAttribute("requestParams") JSONObject params, @RequestAttribute(COOL_PAGE_OP) CrudOption<UserInfoEntity> option) {
        return R.ok(accountQuery.page(params));
    }

    @Override
    @PostMapping("/list")
    protected R list(@RequestAttribute("requestParams") JSONObject params, @RequestAttribute(COOL_LIST_OP) CrudOption<UserInfoEntity> option) {
        return R.ok(accountQuery.list(params));
    }

    @Override
    @GetMapping("/info")
    protected R info(@RequestAttribute("requestParams") JSONObject params, @RequestParam Long id, @RequestAttribute(COOL_INFO_OP) CrudOption<UserInfoEntity> option) {
        return R.ok(accountQuery.info(id));
    }

    @Override
    protected void init(HttpServletRequest request, JSONObject requestParams) {

        setPageOption(createOp().fieldEq(USER_INFO_ENTITY.STATUS, USER_INFO_ENTITY.GENDER,
                USER_INFO_ENTITY.LOGIN_TYPE)
            .keyWordLikeFields(USER_INFO_ENTITY.NICK_NAME, USER_INFO_ENTITY.PHONE));
    }
}
