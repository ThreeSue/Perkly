package com.cool.modules.profile;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.cool.modules.profile.ProfileFacts.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证画像消费去重未知金额隔离与领券不推断消费节奏
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:05:36
 */
class ProfileFactsTest {
    Instant now=Instant.parse("2026-11-10T08:00:00Z");
    Source source(String key,Long amount,int priority,String... links){return new Source(key,1L,2L,3L,4L,now,amount,null,null,priority,List.of(links),false);}
    @Test void linkedBillOrderAndRedemptionAreOneVisit(){
        var batch=visits(1L,List.of(source("B:1",10000L,3,"C:1"),source("C:1",10000L,2,"R:1"),source("R:1",10000L,1)),now);
        assertEquals(1,batch.facts().size());assertEquals(3,batch.facts().get(0).evidence().size());
        assertEquals(10000L,ProfileStatistics.summarize(batch.facts(),now).windows().get(0).consumptionCents());
    }
    @Test void unrelatedSameStoreSameTimeVisitsAreNotMerged(){assertEquals(2,visits(1L,List.of(source("R:1",null,1),source("R:2",null,1)),now).facts().size());}
    @Test void linkedTrustedRedemptionTimeSurvivesMissingPrimaryTime(){
        var missing=new Source("C:1",1L,2L,3L,4L,null,1000L,null,null,2,List.of("R:1"),false);
        var batch=visits(1L,List.of(missing,source("R:1",1000L,1)),now);
        assertEquals(1,batch.facts().size());assertEquals(now,batch.facts().get(0).occurredAt());assertEquals("LINKED_TIME_FALLBACK",batch.issues().get(0).code());
    }
    @Test void unknownAmountsAreNotZeroPriceSamples(){
        var stats=ProfileStatistics.summarize(visits(1L,List.of(source("R:1",null,1)),now).facts(),now).windows().get(0);
        assertEquals(1,stats.visits());assertEquals(0,stats.knownAmountVisits());assertEquals(1,stats.unknownAmountVisits());assertEquals(0,stats.priceBands().get(0).visits());
    }
    @Test void conflictsDoNotCreateFalsePriceEvidence(){
        var batch=visits(1L,List.of(source("C:1",100L,2,"R:1"),source("R:1",200L,1)),now);
        assertNull(batch.facts().get(0).amountCents());assertEquals("AMOUNT_CONFLICT",batch.issues().get(0).code());
    }
    @Test void foreignIdentityAndFutureTimeAreExcluded(){
        var foreign=new Source("R:2",99L,2L,3L,4L,now,100L,null,null,1,List.of("R:1"),false);
        assertTrue(visits(1L,List.of(source("R:1",100L,1),foreign),now).facts().isEmpty());
        assertTrue(visits(1L,List.of(source("R:1",100L,1)),now.minusSeconds(1)).facts().isEmpty());
    }
    @Test void freeClaimsCannotCreateConsumptionPriceOrFrequency(){
        var fact=new Fact("P:1",Kind.FREE_CLAIM,1L,2L,null,4L,now,0L,0L,null,List.of("P:1"),false);
        var stats=ProfileStatistics.summarize(List.of(fact),now);
        assertEquals("NO_CONSUMPTION",stats.sufficiency());assertEquals(1,stats.windows().get(0).freeClaims());assertTrue(stats.windows().get(0).categories().isEmpty());
    }
    @Test void monetaryConversionIsExactAndRejectsInvalidPrecision(){
        assertEquals(1234L,cents(new BigDecimal("12.34")));assertNull(cents(new BigDecimal("1.001")));assertNull(cents(null));assertNull(cents(new BigDecimal("-1")));
    }
}
