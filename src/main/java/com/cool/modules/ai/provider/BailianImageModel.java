package com.cool.modules.ai.provider;
import org.springframework.ai.image.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按百炼兼容协议适配Spring AI单张图片生成和真实参考图编辑
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:30:41
 */
public final class BailianImageModel implements ImageModel {
    private final String endpoint;
    private final String apiKey;
    private final RestClient http;
    public BailianImageModel(String baseUrl,String apiKey,int timeoutSeconds) {
        this.endpoint=baseUrl.replaceAll("/+$","")+"/images/generations";
        this.apiKey=apiKey;
        var factory=new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http=RestClient.builder().requestFactory(factory).build();
    }
    /** 此异常不包含密钥、请求正文、参考图或供应商响应正文。 */
    public static final class ProviderFailure extends RuntimeException {
        private final boolean uncertain;
        public ProviderFailure(String message,boolean uncertain){super(message);this.uncertain=uncertain;}
        public boolean uncertain(){return uncertain;}
    }
    public static final class Options implements ImageOptions {
        private final String model,reference;
        public Options(String model,String reference){this.model=model;this.reference=reference;}
        public Integer getN(){return 1;}
        public String getModel(){return model;}
        public Integer getWidth(){return 1024;}
        public Integer getHeight(){return 1024;}
        public String getResponseFormat(){return "url";}
        public String getStyle(){return null;}
    }
    @Override public ImageResponse call(ImagePrompt prompt) {
        if(!(prompt.getOptions() instanceof Options options))throw new IllegalArgumentException("请选择百炼图片选项");
        if(apiKey==null || apiKey.isBlank())throw new ProviderFailure("未配置BAILIAN_API_KEY",false);
        var body=new LinkedHashMap<String,Object>();
        body.put("model",options.getModel());body.put("prompt",prompt.getInstructions().get(0).getText());
        body.put("n",1);body.put("size","1024x1024");body.put("watermark",false);
        if(options.reference!=null)body.put("image",options.reference);
        try {
            JsonNode data=http.post().uri(URI.create(endpoint)).header("Authorization","Bearer "+apiKey)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                .onStatus(s->s.isError(),(req,res)->{
                    int code=res.getStatusCode().value();
                    throw new ProviderFailure("图片服务HTTP "+code,code>=500 || code==408);
                }).body(JsonNode.class);
            if(data==null || !data.path("data").isArray() || data.path("data").size()!=1)
                throw new ProviderFailure("图片服务返回了无法确认的结果",true);
            var item=data.path("data").get(0);
            String url=item.path("url").asText(null),base64=item.path("b64_json").asText(null);
            if(url!=null && url.isBlank())url=null;
            if(base64!=null && base64.isBlank())base64=null;
            if(url==null && base64==null)throw new ProviderFailure("图片服务未返回可保存的图片",true);
            var metadata=new ImageResponseMetadata();
            metadata.put("providerRequestId",data.path("request_id").asText(""));
            metadata.put("usage",data.path("usage").toString());
            return new ImageResponse(List.of(new ImageGeneration(new Image(url,base64))),metadata);
        } catch(ProviderFailure known){throw known;}
        catch(Exception unknown){throw new ProviderFailure("图片服务连接异常，结果未确定；请勿自动重试",true);}
    }
}
