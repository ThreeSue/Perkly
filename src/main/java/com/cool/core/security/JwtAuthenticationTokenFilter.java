package com.cool.core.security;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import com.cool.core.cache.CoolCache;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.security.jwt.JwtTokenUtil;
import com.cool.core.security.jwt.JwtUser;
import com.cool.core.util.PathUtils;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.util.UserPasswordUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token过滤器
 */
@Order(1)
@Component
@RequiredArgsConstructor
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    final private JwtTokenUtil jwtTokenUtil;
    final private CoolCache coolCache;
    final private IgnoredUrlsProperties ignoredUrlsProperties;
    final private UserInfoMapper appUsers;
    @org.springframework.beans.factory.annotation.Autowired
    private com.cool.modules.merchant.service.MerchantAccountService merchantAccounts;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        if (requestURI.equals("/merchant/auth/login") || requestURI.equals("/merchant/auth/captcha")
                || requestURI.equals("/merchant/auth/refreshToken") || requestURI.equals("/merchant/auth/register")
                || requestURI.equals("/merchant/auth/resubmit") || requestURI.equals("/merchant/auth/registrationCategories")) {
            chain.doFilter(request, response);
            return;
        }
        if (PathUtils.isMatch(ignoredUrlsProperties.getAdminAuthUrls(), requestURI)) {
            // 请求路径在忽略后台鉴权url里支持通配符，放行
            chain.doFilter(request, response);
            return;
        }
        String authToken = request.getHeader("Authorization");
        if (!StrUtil.isEmpty(authToken)) {
            JWT jwt;
            try { jwt = jwtTokenUtil.getTokenInfo(authToken); }
            catch (Exception invalidToken) { response.sendError(401); return; }

            Object userType = jwt.getPayload("userType");
            if (requestURI.startsWith("/merchant/") || Objects.equals(userType, "MERCHANT")) {
                if (!requestURI.startsWith("/merchant/")) { response.sendError(403); return; }
                try {
                    if (Boolean.TRUE.equals(jwt.getPayload("applicant"))) {
                        var application = merchantAccounts.validateApplicant(authToken, false);
                        if (!java.util.Set.of("/merchant/workspace/me", "/merchant/auth/onboarding", "/merchant/auth/logout").contains(requestURI)) {
                            response.sendError(403, "必须通过入驻审批后才能开始经营"); return;
                        }
                        var principal = new com.cool.core.security.jwt.JwtUser(application.getId(), application.getUsername(), "",
                                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MERCHANT")), true);
                        principal.setUserTypeEnum(UserTypeEnum.MERCHANT);
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                        request.setAttribute("merchantApplication", application);
                        chain.doFilter(request, response); return;
                    }
                    var user = merchantAccounts.validate(authToken, false);
                    if (!"OWNER".equals(user.getMerchantRole()) && !java.util.Set.of(
                            "/merchant/staff/me", "/merchant/redemption/preview", "/merchant/redemption/confirm",
                            "/merchant/redemption/page", "/merchant/auth/logout", "/merchant/auth/changePassword").contains(requestURI)) {
                        response.sendError(403, "当前门店账号无此权限"); return;
                    }
                    var principal = merchantAccounts.principal(user);
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute("merchantUserId", user.getId());
                    request.setAttribute("tokenInfo", jwt);
                } catch (Exception invalidMerchant) { response.sendError(401); return; }
                chain.doFilter(request, response);
                return;
            }
            if (requestURI.startsWith("/admin/") && !Objects.equals(userType, "ADMIN")) {
                response.sendError(403); return;
            }
            if (Objects.equals(userType, UserTypeEnum.APP.name())) {
                // app
                handlerAppRequest(request, jwt, authToken);
            } else {
                // admin
                handlerAdminRequest(request, jwt, authToken);
            }
        }
        chain.doFilter(request, response);
    }
    /**
     * 处理app请求
     */
    private void handlerAppRequest(HttpServletRequest request, JWT jwt, String authToken) {
        String userId = jwt.getPayload("userId").toString();
        if (ObjectUtil.isNotEmpty(userId)
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = coolCache.get("app:userDetails:" + userId,
                JwtUser.class);
            if (jwtTokenUtil.validateToken(authToken) && userDetails != null
                    && Boolean.FALSE.equals(jwt.getPayload("isRefresh"))
                    && UserPasswordUtil.matchesCredentialVersion(appUsers.selectOneById(userId), jwt.getPayload("credentialVersion"))) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("userId", jwt.getPayload("userId"));
                request.setAttribute("tokenInfo", jwt);
            }
        }
    }

    /**
     * 处理后台请求
     */
    private void handlerAdminRequest(HttpServletRequest request, JWT jwt, String authToken) {
        String username = jwt.getPayload("username").toString();
        if (username != null
            && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = coolCache.get("admin:userDetails:" + username,
                JwtUser.class);
            Integer passwordV = Convert.toInt(jwt.getPayload("passwordVersion"));
            Integer rv = coolCache.get("admin:passwordVersion:" + jwt.getPayload("userId"),
                Integer.class);
            if (jwtTokenUtil.validateToken(authToken, username) && Objects.equals(passwordV, rv)
                && userDetails != null && Boolean.FALSE.equals(jwt.getPayload("isRefresh"))
                && merchantAccounts.platformAccount(Convert.toLong(jwt.getPayload("userId")))) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("adminUsername", jwt.getPayload("username"));
                request.setAttribute("adminUserId", jwt.getPayload("userId"));
                request.setAttribute("tokenInfo", jwt);
            }
        }
    }
}
