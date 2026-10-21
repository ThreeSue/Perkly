package com.cool.modules.coupon.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;
import com.cool.core.exception.CoolException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mybatisflex.core.row.Row;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一购券金额时间券状态及不可预测券码和二维码规则
 * @Version: v1.1
 * @Date: 2026-10-20
 * @Time: 21:14:31
 */
public final class CouponTradeRules {
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private CouponTradeRules() {}
    public static void require(boolean condition, String message) { if (!condition) throw new CoolException(message); }
    public static long number(Object value) { return ((Number)value).longValue(); }
    public static BigDecimal decimal(Object value) { return new BigDecimal(value.toString()); }
    public static long cents(Object value) { return decimal(value).movePointRight(2).longValueExact(); }
    public static Date date(Object value) {
        if (value == null) return null;
        if (value instanceof Date d) return d;
        if (value instanceof LocalDateTime d) return Date.from(d.atZone(ZONE).toInstant());
        return DateUtil.parse(value.toString());
    }
    public static String time(Object value) {
        Date d = date(value);
        return d == null ? null : d.toInstant().atZone(ZONE).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    public static String couponState(Row row, Date now) {
        int status = ((Number)row.get("status")).intValue();
        if (status == 1) return "USED";
        if (status == 2) return "VOID";
        if (!date(row.get("valid_to")).after(now)) return "EXPIRED";
        if (date(row.get("valid_from")).after(now)) return "NOT_STARTED";
        return "UNUSED";
    }
    public static String code() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
    public static byte[] qrPng(String code) {
        try {
            var matrix = new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 320, 320,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
            BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_BYTE_BINARY);
            for (int x=0;x<320;x++) for (int y=0;y<320;y++) image.setRGB(x,y,matrix.get(x,y) ? 0xff000000 : 0xffffffff);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) throw new IllegalStateException("PNG编码器不可用");
            return out.toByteArray();
        } catch (Exception e) { throw new CoolException("二维码生成失败，请重试", e); }
    }
    public static String qrImage(String code) { return "data:image/png;base64," + Base64.getEncoder().encodeToString(qrPng(code)); }
    public static BigDecimal discount(JSONObject rule, Long consumptionCents) {
        require(consumptionCents == null || (consumptionCents >= 0 && consumptionCents <= 9_999_999_999L), "消费金额须为有效的整数分");
        String type = rule.getStr("type");
        if (Set.of("PACKAGE", "EXPERIENCE", "EXCHANGE").contains(type)) return BigDecimal.ZERO.setScale(2);
        require(consumptionCents != null && consumptionCents > 0, "此类型优惠券须填写实际消费金额（分）");
        BigDecimal amount = BigDecimal.valueOf(consumptionCents, 2);
        BigDecimal min = rule.getBigDecimal("minSpend", BigDecimal.ZERO);
        require(amount.compareTo(min) >= 0, "实际消费金额未达到使用门槛");
        BigDecimal result = switch(type) {
            case "FULL_REDUCTION" -> rule.getBigDecimal("discountAmount");
            case "CASH" -> rule.getBigDecimal("deductAmount");
            case "DISCOUNT" -> amount.subtract(amount.multiply(rule.getBigDecimal("discountRate")).divide(BigDecimal.TEN,2,RoundingMode.HALF_UP));
            default -> throw new CoolException("优惠券规则不可用");
        };
        if ("DISCOUNT".equals(type) && rule.getBigDecimal("maxDiscount") != null) result = result.min(rule.getBigDecimal("maxDiscount"));
        return result.min(amount).setScale(2, RoundingMode.HALF_UP);
    }
}
