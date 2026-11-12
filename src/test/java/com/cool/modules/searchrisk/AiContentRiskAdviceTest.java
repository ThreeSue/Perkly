package com.cool.modules.searchrisk;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
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
  var method=com.cool.modules.ai.controller.AiImageController.class.getDeclaredMethod("create",com.cool.modules.ai.service.AiTaskService.Create.class);
  var body=mapper.readTree("{\"prompt\":\"new prompt\",\"history\":[{\"text\":\"old input\"}],\"password\":\"secret\",\"apiKey\":\"key\",\"previous\":{\"text\":\"old draft\"}}");
  advice.afterBodyRead(body,null,new MethodParameter(method,0),Object.class,MappingJackson2HttpMessageConverter.class);
  verify(risk).checkContent("new prompt","AI_INPUT","AiImageController",null);verifyNoMoreInteractions(risk);
 }
 @Test void outputSuggestionsAreCheckedAsSystemScene() throws Exception {
  var risk=mock(SearchRiskService.class);var mapper=new ObjectMapper();var advice=new AiContentRiskAdvice(risk,mapper);
  var method=com.cool.modules.ai.controller.AiImageController.class.getDeclaredMethod("create",com.cool.modules.ai.service.AiTaskService.Create.class);
  advice.beforeBodyWrite(mapper.readTree("{\"message\":\"reply\",\"suggestions\":[\"next step\"]}"),new MethodParameter(method,-1),MediaType.APPLICATION_JSON,MappingJackson2HttpMessageConverter.class,null,null);
  verify(risk).checkContent("reply","AI_OUTPUT","AiImageController",null);verify(risk).checkContent("next step","AI_OUTPUT","AiImageController",null);
 }
}

