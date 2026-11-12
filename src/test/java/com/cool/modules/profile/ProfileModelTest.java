package com.cool.modules.profile;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证画像模型不能杜撰标签证据或自行提高数据充分度
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:45:00
 */
class ProfileModelTest {
    private final ProfileSourceReader.Evidence evidence=new ProfileSourceReader.Evidence(new ProfileFacts.Batch(List.of(),List.of()),
        ProfileStatistics.summarize(List.of(),Instant.parse("2026-11-10T00:00:00Z")),List.of(new ProfileSourceReader.Content("M:1","咖啡与面包",List.of("P:1"))),java.util.Map.of());
    @Test void groundedTagCannotOverrideDataSufficiency(){
        var value=ProfileModel.validate(new ProfileModel.Interpretation(List.of(new ProfileModel.Tag("咖啡","M:1","咖啡与面包",List.of("P:1"))),"SUPPORTED"),evidence);
        assertEquals("NO_CONSUMPTION",value.sufficiency());
    }
    @Test void inventedQuoteIsRejected(){assertThrows(IllegalArgumentException.class,()->ProfileModel.validate(new ProfileModel.Interpretation(List.of(new ProfileModel.Tag("海鲜","M:1","喜欢海鲜",List.of("P:1"))),"SUPPORTED"),evidence));}
    @Test void ForeignEvidenceIsRejected(){assertThrows(IllegalArgumentException.class,()->ProfileModel.validate(new ProfileModel.Interpretation(List.of(new ProfileModel.Tag("咖啡","M:1","咖啡",List.of("P:99"))),"SUPPORTED"),evidence));}
}
