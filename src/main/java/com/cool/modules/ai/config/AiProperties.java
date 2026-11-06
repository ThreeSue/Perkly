package com.cool.modules.ai.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 从IDEA进程环境或私有配置加载百炼连接且不生成含密钥的字符串
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:35:46
 */
@Getter @Setter @Component @ConfigurationProperties(prefix="perkly.ai")
public class AiProperties {
    private String baseUrl="https://llm-o00jm6uas5b7l5b5.cn-beijing.maas.aliyuncs.com/compatible-mode/v1";
    @org.springframework.beans.factory.annotation.Value("${BAILIAN_API_KEY:}")
    private String apiKey;
    private String textModel="qwen3.8-flash";
    private String imageModel="qwen-image-3.0";
    private int timeoutSeconds=180;
    public String getApiKey(){
        String injected=System.getenv("BAILIAN_API_KEY");
        return injected==null || injected.isBlank()?apiKey:injected;
    }
    public boolean configured(){return getApiKey()!=null && !getApiKey().isBlank();}
}
