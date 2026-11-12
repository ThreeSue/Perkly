package com.cool.modules.searchrisk;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cool.modules.searchrisk.mapper.*;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证搜索词策略和请求内去重
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 11:40:00
 */
class SearchRiskTest {
 SearchWord rule(String text,String mode){var w=new SearchWord();w.setWord(text);w.setMode(mode);w.setEnabled(true);return w;}
 @Test void fullWidthAndCaseMatch(){assertEquals(1,SearchRiskService.matches("ＡＢＣ",List.of(rule("abc","BLOCK"))).size());}
 @Test void whitelistIsExactNotSubstring(){var r=List.of(rule("bad","BLOCK"),rule("badminton","ALLOW"));assertTrue(SearchRiskService.matches("badminton",r).isEmpty());assertEquals(1,SearchRiskService.matches("badminton bad",r).size());}
 @Test void disabledRuleDoesNotMatch(){var w=rule("bad","BLOCK");w.setEnabled(false);assertTrue(SearchRiskService.matches("bad",List.of(w)).isEmpty());}
 @Test void reviewRecordsOnlyOnceInOneRequest(){
  var words=mock(SearchWordMapper.class);var recorder=mock(SearchRiskRecorder.class);
  when(words.selectListByQuery(any())).thenReturn(List.of(rule("review","REVIEW")));
  var service=new SearchRiskService(words,recorder);
  var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/app/recommendation/page");
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
  try{service.check(" review ","STORE");service.check("review","STORE");verify(recorder,times(1)).persist(argThat(r->r.getUserId()==null&&" review ".equals(r.getContent())&&"/app/recommendation/page".equals(r.getSource())));}
  finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
 }
 @Test void blockIsRecordedBeforeRejection(){var words=mock(SearchWordMapper.class);var recorder=mock(SearchRiskRecorder.class);when(words.selectListByQuery(any())).thenReturn(List.of(rule("block","BLOCK")));assertThrows(com.cool.core.exception.CoolException.class,()->new SearchRiskService(words,recorder).check("block","STORE"));verify(recorder).persist(any());}
 @Test void existingTokenIsDeniedWhenBannedAndWorksAfterUnban() throws Exception {
  var tokens=mock(com.cool.core.security.jwt.JwtTokenUtil.class);
  var cache=mock(com.cool.core.cache.CoolCache.class);
  var users=mock(com.cool.modules.user.mapper.UserInfoMapper.class);
  var user=new com.cool.modules.user.entity.UserInfoEntity();user.setId(123L);user.setPassword("test-hash");user.setStatus(0);
  var jwt=cn.hutool.jwt.JWT.create().setPayload("userType","APP").setPayload("userId",123L).setPayload("isRefresh",false).setPayload("credentialVersion",com.cool.modules.user.util.UserPasswordUtil.credentialVersion(user));
  when(tokens.getTokenInfo("existing-token")).thenReturn(jwt);when(tokens.validateToken("existing-token")).thenReturn(true);
  when(users.selectOneById("123")).thenReturn(user);
  when(cache.get("app:userDetails:123",com.cool.core.security.jwt.JwtUser.class)).thenReturn(new com.cool.core.security.jwt.JwtUser(123L,List.of(),true));
  var ignored=new com.cool.core.security.IgnoredUrlsProperties();ignored.setAdminAuthUrls(List.of("/app/**"));
  var filter=new com.cool.core.security.JwtAuthenticationTokenFilter(tokens,cache,ignored,users);
  try {
   var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/app/discovery/stores");request.addHeader("Authorization","existing-token");
   var response=new org.springframework.mock.web.MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
   filter.doFilter(request,response,chain);assertEquals(403,response.getStatus());verifyNoInteractions(chain);
   user.setStatus(1);
   request=new org.springframework.mock.web.MockHttpServletRequest("GET","/app/discovery/stores");request.addHeader("Authorization","existing-token");
   response=new org.springframework.mock.web.MockHttpServletResponse();filter.doFilter(request,response,chain);
   assertEquals(200,response.getStatus());verify(chain).doFilter(request,response);
   assertEquals(123L,((com.cool.core.security.jwt.JwtUser)org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId());
  } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
}
