package com.cool.modules.searchrisk;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 可审计的内容风险词库与增量巡检
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 16:38:51
 */

class AiContentRiskAdviceTest {
 @Test void onlyNewInputIsCheckedAndSecretsAndHistoryAreExcluded() throws Exception {
  var risk=mock(SearchRiskService.class);var mapper=new ObjectMapper();var advice=new AiContentRiskAdvice(risk,mapper);
  var method=com.cool.modules.ai.app.AppUserAssistantController.class.getDeclaredMethod("chat",com.cool.modules.ai.app.UserAssistantModels.Chat.class);
  var body=mapper.readTree("{\"message\":\"new input\",\"history\":[{\"text\":\"old input\"}],\"password\":\"secret\",\"apiKey\":\"key\",\"previous\":{\"text\":\"old draft\"}}");
  advice.afterBodyRead(body,null,new MethodParameter(method,0),Object.class,MappingJackson2HttpMessageConverter.class);
  verify(risk).checkContent("new input","AI_INPUT","AppUserAssistantController:message",null);verifyNoMoreInteractions(risk);
 }
 @Test void imageCreationAndReadMetadataDoNotBecomeSystemAuthoredContent() throws Exception {
  var risk=mock(SearchRiskService.class);var mapper=new ObjectMapper();var advice=new AiContentRiskAdvice(risk,mapper);
  var create=com.cool.modules.ai.controller.AiImageController.class.getDeclaredMethod("create",com.cool.modules.ai.service.AiTaskService.Create.class);
  var preview=com.cool.modules.ai.controller.AiImageController.class.getDeclaredMethod("prompt",com.cool.modules.ai.service.AiAccessService.Draft.class);
  var body=mapper.readTree("{\"prompt\":\"不生成二维码、水印或联系方式。\",\"name\":\"某商家\"}");
  for(var method:java.util.List.of(create,preview))
   advice.afterBodyRead(body,null,new MethodParameter(method,0),Object.class,MappingJackson2HttpMessageConverter.class);
  verifyNoInteractions(risk);
 }
}

