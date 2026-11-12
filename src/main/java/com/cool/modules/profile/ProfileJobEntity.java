package com.cool.modules.profile;
import com.mybatisflex.annotation.*;
import lombok.Data;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 合并用户画像刷新请求并记录有限重试及租约
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:12:00
 */

@Data @Table("profile_job") @org.dromara.autotable.annotation.Ignore
public class ProfileJobEntity {
 @Id private Long customerId;
 private String state;
 private Integer attempts;
 private Long requestedVersion;
 private Long runningVersion;
 private Date requestedAt;
 private Date nextAttemptAt;
 private Date leaseUntil;
 private String errorCode;
}

