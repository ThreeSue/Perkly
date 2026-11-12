package com.cool.modules.profile;
import com.mybatisflex.annotation.*;
import lombok.Data;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存运营纠正的排除标签且不改写原始消费证据
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 17:05:00
 */
@Data @Table("profile_correction") @org.dromara.autotable.annotation.Ignore
public class ProfileCorrectionEntity {
 @Id private Long customerId;
 private String excludedLabels;
 private Date updatedAt;
}
