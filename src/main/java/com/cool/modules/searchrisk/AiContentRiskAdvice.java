package com.cool.modules.searchrisk;
import com.fasterxml.jackson.databind.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.*;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import java.lang.reflect.Type;
import java.util.Set;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

@ControllerAdvice @lombok.RequiredArgsConstructor
public class AiContentRiskAdvice extends RequestBodyAdviceAdapter implements ResponseBodyAdvice<Object> {
 private final SearchRiskService risk; private final ObjectMapper json;
 private static final Set<String> INPUT=Set.of("message","prompt","instruction","requirement","text","question","title","content","name","description","keyword");
 private static final Set<String> OUTPUT=Set.of("message","answer","content","text","reply","summary","title","name","description","question","reason","explanation","suggestion","suggestions","prompt");
 private boolean ai(MethodParameter p){return p.getContainingClass().getPackageName().startsWith("com.cool.modules.ai");}
 @Override public boolean supports(MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){return ai(p);}
 @Override public Object afterBodyRead(Object body,HttpInputMessage input,MethodParameter p,Type t,Class<? extends HttpMessageConverter<?>> c){
  visit(json.valueToTree(body),INPUT,"AI_INPUT",p.getContainingClass().getSimpleName(),"");return body;
 }
 @Override public boolean supports(MethodParameter p,Class<? extends HttpMessageConverter<?>> c){return ai(p);}
 @Override public Object beforeBodyWrite(Object body,MethodParameter p,MediaType m,Class<? extends HttpMessageConverter<?>> c,ServerHttpRequest req,ServerHttpResponse response){
  visit(json.valueToTree(body),OUTPUT,"AI_OUTPUT",p.getContainingClass().getSimpleName(),"");return body;
 }
 private void visit(JsonNode node,Set<String> fields,String scene,String business,String field){
  if(node==null||node.isNull())return;
  if(node.isTextual()){if(fields.contains(field))risk.checkContent(node.asText(),scene,business,null);return;}
  if(node.isArray()){node.forEach(n->visit(n,fields,scene,business,field));return;}
  if(node.isObject())node.fields().forEachRemaining(e->{if(!Set.of("password","apiKey","api-key","token","authorization","secret","history","previous","pageContext").contains(e.getKey()))visit(e.getValue(),fields,scene,business,e.getKey());});
 }
}

