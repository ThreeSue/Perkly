package com.cool.modules.wallet.service;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 拒绝浮点金额被JSON默认转换静默截断为整数分
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 02:01:01
 */
public class IntegerCentsDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT))
            throw com.fasterxml.jackson.databind.exc.InvalidFormatException.from(parser, "金额须为整数分", parser.getText(), Long.class);
        return parser.getLongValue();
    }
}
