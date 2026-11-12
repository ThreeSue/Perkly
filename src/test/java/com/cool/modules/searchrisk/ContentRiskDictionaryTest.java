package com.cool.modules.searchrisk;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 可审计的内容风险词库与增量巡检
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 16:27:26
 */

class ContentRiskDictionaryTest {
 private final ContentRiskDictionary dictionary=new ContentRiskDictionary();
 private SearchWord rule(String word,String mode,boolean enabled){var w=new SearchWord();w.setWord(word);w.setMode(mode);w.setEnabled(enabled);return w;}
 @Test void bundledDataActuallyDetectsAndOnlyReviews(){var hits=dictionary.find("你是傻逼",List.of());assertFalse(hits.isEmpty());assertTrue(hits.stream().allMatch(h->h.mode().equals("REVIEW")));assertTrue(hits.stream().anyMatch(h->h.source().equals("HOUBB_DATA_1.0.0")));for(var h:hits)assertEquals(h.matchedText(),"你是傻逼".substring(h.start(),h.end()));}
 @Test void disabledCustomOverridesBundled(){assertTrue(dictionary.find("傻逼",List.of(rule("傻逼","BLOCK",false))).isEmpty());}
 @Test void whitelistOnlyAllowsExactText(){var rules=List.of(rule("riskword","BLOCK",true),rule("riskword safe","ALLOW",true));assertTrue(dictionary.find("riskword safe",rules).isEmpty());assertFalse(dictionary.find("riskword safe extra",rules).isEmpty());}
 @Test void offsetsReferToOriginalWithEmojiAndCompatibilityExpansion(){String text="😀ＡＢＣ和ﬃ";var hits=dictionary.find(text,List.of(rule("abc","BLOCK",true),rule("ffi","REVIEW",true)));assertEquals(2,hits.size());assertEquals("ＡＢＣ",hits.get(0).matchedText());assertEquals(2,hits.get(0).start());assertEquals(5,hits.get(0).end());assertEquals("ﬃ",hits.get(1).matchedText());}
 @Test void customClassificationOverridesBundled(){var hits=dictionary.find("傻逼",List.of(rule("傻逼","BLOCK",true)));assertEquals(1,hits.size());assertEquals("BLOCK",hits.get(0).mode());assertEquals("CUSTOM",hits.get(0).source());}
}

