package com.cool.modules.profile;
import com.cool.modules.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 只用硬编码虚构样本验证模型结构输出不连接数据库
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:50:00
 */
class ProfileSyntheticLiveTest {
    @Test void entirelySyntheticCoffeeSample() {
        String key=System.getenv("BAILIAN_API_KEY");Assumptions.assumeTrue(key!=null&&!key.isBlank());
        var config=new AiProperties();config.setApiKey(key);
        Instant at=Instant.parse("2026-11-01T04:00:00Z");
        var fact=new ProfileFacts.Fact("DEMO-ONLY",ProfileFacts.Kind.VISIT,1L,1L,1L,1L,at,2500L,2500L,0L,List.of("DEMO-ONLY"),false);
        var evidence=new ProfileSourceReader.Evidence(new ProfileFacts.Batch(List.of(fact),List.of()),ProfileStatistics.summarize(List.of(fact),at),
            List.of(new ProfileSourceReader.Content("SYNTHETIC","虚构咖啡测试店：提供咖啡与面包，此数据完全为测试编造。",List.of("DEMO-ONLY"))),Map.of(1L,"虚构咖啡分类"));
        var value=new ProfileModel(config,new ObjectMapper().findAndRegisterModules()).interpret(evidence);
        assertEquals("LIMITED",value.sufficiency());assertNotNull(ProfileModel.validate(value,evidence));
    }
}
