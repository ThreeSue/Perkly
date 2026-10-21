package com.cool.modules.coupon;

import static org.junit.jupiter.api.Assertions.*;
import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.service.CouponTradeRules;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 解码实际生成二维码并验证六类券核销金额边界
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:26:34
 */
class CouponTradeRulesTest {
    @Test void pngCanBeScannedBackToTheExactRandomCouponCode() throws Exception {
        String code=CouponTradeRules.code();
        var image=ImageIO.read(new ByteArrayInputStream(CouponTradeRules.qrPng(code)));
        assertEquals(320,image.getWidth()); assertEquals(320,image.getHeight());
        LuminanceSource source=new LuminanceSource(320,320) {
            public byte[] getRow(int y,byte[] row) {
                if(row==null || row.length<320) row=new byte[320];
                for(int x=0;x<320;x++) row[x]=(byte)(image.getRGB(x,y)&255); return row;
            }
            public byte[] getMatrix() {
                byte[] data=new byte[320*320];
                for(int y=0;y<320;y++) System.arraycopy(getRow(y,null),0,data,y*320,320); return data;
            }
        };
        assertEquals(code,new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText());
        assertNotEquals(code,CouponTradeRules.code());
    }
    @Test void fullReductionRequiresThresholdAndNeverExceedsConsumption() {
        var rule=new JSONObject().set("type","FULL_REDUCTION").set("minSpend",100).set("discountAmount",20);
        assertEquals(2000L,CouponTradeRules.cents(CouponTradeRules.discount(rule,10000L)));
        assertThrows(CoolException.class,()->CouponTradeRules.discount(rule,9999L));
        assertThrows(CoolException.class,()->CouponTradeRules.discount(rule,null));
    }
    @Test void rateUsesRoundingAndMaximumDiscount() {
        var rule=new JSONObject().set("type","DISCOUNT").set("discountRate",8.8).set("maxDiscount",10);
        assertEquals(1000L,CouponTradeRules.cents(CouponTradeRules.discount(rule,10000L)));
        rule.remove("maxDiscount");
        assertEquals(1200L,CouponTradeRules.cents(CouponTradeRules.discount(rule,10000L)));
        assertEquals(1L,CouponTradeRules.cents(CouponTradeRules.discount(rule,10L)));
    }
    @Test void cashCouponCapsAtConsumptionAndRejectsNegativeAmounts() {
        var rule=new JSONObject().set("type","CASH").set("deductAmount",20);
        assertEquals(1000L,CouponTradeRules.cents(CouponTradeRules.discount(rule,1000L)));
        assertThrows(CoolException.class,()->CouponTradeRules.discount(rule,-1L));
        assertThrows(CoolException.class,()->CouponTradeRules.discount(rule,0L));
        assertThrows(CoolException.class,()->CouponTradeRules.discount(rule,10_000_000_000L));
    }
    @Test void packageExperienceExchangeRedeemContentsWithoutInventingCashDiscount() {
        for(String type:java.util.List.of("PACKAGE","EXPERIENCE","EXCHANGE")) {
            var rule=new JSONObject().set("type",type);
            assertEquals(0L,CouponTradeRules.cents(CouponTradeRules.discount(rule,null)));
        }
    }
}
