package com.cool.modules.user.mapper.query;

import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryTable;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.RawQueryColumn;
import java.util.List;
import static com.mybatisflex.core.query.QueryMethods.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 使用MyBatis-Flex构造运营端账号分类和微信关联查询
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 14:51:01
 */
public final class AdminUserAccountQueries {
    private static final QueryTable USER = new QueryTable("user_info").as("u");
    private static final QueryTable WECHAT = new QueryTable("user_wx").as("w");
    private AdminUserAccountQueries() {}

    public static QueryWrapper list(JSONObject params) {
        QueryWrapper query = projection();
        String group = params.getStr("accountGroup", "formal");
        if (!List.of("formal", "history", "all").contains(group)) throw new CoolException("账号分类参数错误");
        if ("formal".equals(group)) query.and(phone());
        if ("history".equals(group)) query.and(not(phone()));
        Integer status = params.getInt("status");
        if (status != null) {
            if (status < 0 || status > 2) throw new CoolException("账号状态参数错误");
            query.and(col(USER, "status").eq(status));
        } else if ("formal".equals(group)) query.and(col(USER, "status").in(0, 1));
        for (String key : List.of("wechatBound", "passwordSet")) {
            Integer flag = params.getInt(key);
            if (flag == null) continue;
            if (flag != 0 && flag != 1) throw new CoolException("绑定筛选参数错误");
            QueryCondition condition = "wechatBound".equals(key) ? wechatBound() : passwordSet();
            query.and(flag == 1 ? condition : not(condition));
        }
        String keyword = params.getStr("keyWord", "").trim();
        if (!keyword.isEmpty()) {
            // 保留原来的ID字符比较及LIKE通配符规则，用户输入使用参数绑定。
            query.and(raw("CAST(u.id AS CHAR)=?", keyword)
                    .or(col(USER, "nick_name").likeRaw("%" + keyword + "%"))
                    .or(col(USER, "phone").likeRaw("%" + keyword + "%")));
        }
        String field = switch (params.getStr("order", "createTime")) {
            case "id" -> "id";
            case "nickName" -> "nick_name";
            case "updateTime" -> "update_time";
            default -> "create_time";
        };
        boolean asc = "asc".equalsIgnoreCase(params.getStr("sort", "desc"));
        return query.orderBy(asc ? col(USER, field).asc() : col(USER, field).desc(), col(USER, "id").desc());
    }

    public static QueryWrapper info(Long id) {
        return projection().and(col(USER, "id").eq(id));
    }

    public static QueryWrapper legacyAccounts(Long id) {
        QueryTable old = new QueryTable("user_info").as("old");
        QueryColumn phone = col(old, "phone");
        QueryColumn masked = case_().when(phone.isNull(true).or(phone.eq("")))
                .then(new RawQueryColumn("NULL"))
                .else_(concat(left(phone, 3), string("****"), right(phone, 4))).end().as("phoneMasked");
        QueryWrapper related = select(number(1)).from(WECHAT).where(col(WECHAT, "user_id").eq(id))
                .and(wechatIdentityMatches(old));
        return select(col(old, "id"), col(old, "nick_name").as("nickName"), masked).from(old)
                .where(col(old, "id").ne(id)).and(hasUnionid(old)).and(exists(related));
    }

    private static QueryWrapper projection() {
        QueryWrapper associated = select(col(WECHAT, "user_id")).from(WECHAT)
                .where(col(WECHAT, "user_id").ne(col(USER, "id")))
                .and(hasUnionid(USER)).and(wechatIdentityMatches(USER)).limit(1);
        return select(col(USER, "id"), col(USER, "nick_name").as("nickName"),
                col(USER, "avatar_url").as("avatarUrl"), col(USER, "phone"), col(USER, "gender"), col(USER, "status"),
                col(USER, "create_time").as("createTime"), col(USER, "update_time").as("updateTime"),
                case_().when(wechatBound()).then(1).else_(0).end().as("wechatBound"),
                case_().when(passwordSet()).then(1).else_(0).end().as("passwordSet"),
                case_().when(phone()).then(0).else_(1).end().as("historyPending"),
                column(associated).as("associatedAccountId")).from(USER);
    }

    private static QueryCondition phone() {
        return raw("COALESCE(u.phone,'') REGEXP ?", "^1[3-9][0-9]{9}$");
    }

    private static QueryCondition passwordSet() {
        return col(USER, "password").isNotNull(true).and(col(USER, "password").ne(""));
    }

    private static QueryCondition wechatBound() {
        QueryCondition direct = col(WECHAT, "user_id").eq(col(USER, "id"));
        QueryCondition legacy = col(WECHAT, "user_id").isNull(true)
                .and(hasUnionid(USER)).and(wechatIdentityMatches(USER));
        return exists(select(number(1)).from(WECHAT).where(direct.or(legacy)));
    }

    private static QueryCondition hasUnionid(QueryTable user) {
        return col(user, "unionid").isNotNull(true).and(col(user, "unionid").ne(""));
    }

    private static QueryCondition wechatIdentityMatches(QueryTable user) {
        return col(WECHAT, "openid").eq(col(user, "unionid"))
                .or(col(WECHAT, "unionid").eq(col(user, "unionid")));
    }

    private static QueryColumn col(QueryTable table, String name) {
        return new QueryColumn(table, name);
    }
}
