package com.cool.modules.searchrisk;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.cool.core.exception.CoolException;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证人工复核原因选填且保留状态和长度约束
 * @Version: v1.0
 * @Date: 2026-11-14
 */
class RiskManualReasonTest {
 @Test void optionalReason(){assertNull(SearchRiskController.reviewReason("CONFIRMED",null));assertNull(SearchRiskController.reviewReason("IGNORED"," \n "));assertEquals("核对原文",SearchRiskController.reviewReason("FALSE_POSITIVE"," 核对原文 "));}
 @Test void constraintsRemain(){assertThrows(CoolException.class,()->SearchRiskController.reviewReason("PENDING",null));assertThrows(CoolException.class,()->SearchRiskController.reviewReason(null,null));assertThrows(CoolException.class,()->SearchRiskController.reviewReason("IGNORED","字".repeat(201)));}
}
