package com.cool.modules.workspace;

import com.cool.core.exception.CoolExceptionHandler;
import com.cool.modules.workspace.controller.WorkspaceNoticeController;
import com.cool.modules.workspace.entity.WorkspaceNoticeRecipientEntity;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.http.HttpServletRequest;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 通过HTTP绑定实际通知服务验证发送详情及已读权限边界
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:27:19
 */
class WorkspaceNoticeControllerTest extends WorkspaceNoticeFixture {
    MockMvc mvc;
    @BeforeEach void http(){
        HttpServletRequest request=mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenAnswer(a->((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest().getRequestURI());
        when(request.getAttribute("merchantUserId")).thenAnswer(a->((ServletRequestAttributes)RequestContextHolder.currentRequestAttributes()).getRequest().getAttribute("merchantUserId"));
        mvc=MockMvcBuilders.standaloneSetup(new WorkspaceNoticeController(service,request)).setControllerAdvice(new CoolExceptionHandler()).build();
    }
    @Test void sendAndForeignReadOverHttp() throws Exception {
        mvc.perform(post("/merchant/governance/notices/send").requestAttr("merchantUserId",1L).contentType("application/json")
            .content("{\"requestId\":\"request_http\",\"title\":\"标题\",\"content\":\"正文\",\"recipientIds\":[2]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data").value(99));
        mvc.perform(get("/merchant/governance/notices/detail?id=99").requestAttr("merchantUserId",1L))
            .andExpect(jsonPath("$.code").value(404));
        mvc.perform(post("/merchant/governance/notices/read").requestAttr("merchantUserId",1L).contentType("application/json").content("{\"id\":99}"))
            .andExpect(jsonPath("$.code").value(404));
    }
    @Test void unreadAndLoginValidationOverHttp() throws Exception {
        when(recipients.selectCountByQuery(any(QueryWrapper.class))).thenReturn(3L);
        mvc.perform(get("/merchant/governance/notices/unread").requestAttr("merchantUserId",1L)).andExpect(jsonPath("$.data").value(3));
        mvc.perform(get("/merchant/governance/notices/unread")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/merchant/governance/notices/page?pageSize=101").requestAttr("merchantUserId",1L)).andExpect(jsonPath("$.code").value(500));
    }
}
