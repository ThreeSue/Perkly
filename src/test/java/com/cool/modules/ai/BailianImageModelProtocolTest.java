package com.cool.modules.ai;
import com.cool.modules.ai.provider.BailianImageModel;
import org.springframework.ai.image.ImagePrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 用本地协议服务验证百炼原图字段请求路径单张限制和失败不自动重试
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 17:08:33
 */
class BailianImageModelProtocolTest {
    @Test void generationAndEditUseOneEndpointAndActualImageField()throws Exception {
        var json=new ObjectMapper();var bodies=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        var paths=new ArrayList<String>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            paths.add(exchange.getRequestURI().getPath());bodies.add(json.readTree(exchange.getRequestBody()));
            byte[] body="{\"data\":[{\"url\":\"https://example.oss-cn-beijing.aliyuncs.com/result.png\"}],\"request_id\":\"mock-request\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            var model=new BailianImageModel("http://127.0.0.1:"+server.getAddress().getPort()+"/compatible-mode/v1","test-not-secret",5);
            model.call(new ImagePrompt("实际表单内容",new BailianImageModel.Options("qwen-image-3.0",null)));
            model.call(new ImagePrompt("仅调整原图背景",new BailianImageModel.Options("qwen-image-3.0","data:image/png;base64,test-fixture")));
            assertEquals(List.of("/compatible-mode/v1/images/generations","/compatible-mode/v1/images/generations"),paths);
            assertFalse(bodies.get(0).has("image"));assertEquals("data:image/png;base64,test-fixture",bodies.get(1).path("image").asText());
            assertEquals(1,bodies.get(1).path("n").asInt());assertEquals("1024x1024",bodies.get(1).path("size").asText());
        }finally{server.stop(0);}
    }
    @Test void providerServerFailureIsUncertainAndNotAutomaticallyRetried()throws Exception {
        var count=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{count.incrementAndGet();byte[] body="do not expose raw provider body".getBytes();
            e.sendResponseHeaders(503,body.length);e.getResponseBody().write(body);e.close();});server.start();
        try {
            var model=new BailianImageModel("http://127.0.0.1:"+server.getAddress().getPort(),"test-not-secret",5);
            var failure=assertThrows(BailianImageModel.ProviderFailure.class,()->model.call(new ImagePrompt("测试",
                new BailianImageModel.Options("qwen-image-3.0",null))));
            assertTrue(failure.uncertain());assertEquals(1,count.get());assertFalse(failure.getMessage().contains("raw provider"));
        }finally{server.stop(0);}
    }
}
