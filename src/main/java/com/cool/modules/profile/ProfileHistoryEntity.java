package com.cool.modules.profile;
import com.mybatisflex.annotation.*;
import lombok.Data;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保留画像成功版本完整证据以便追溯重算差异
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:55:00
 */
@Data @Table("profile_history") @org.dromara.autotable.annotation.Ignore
public class ProfileHistoryEntity {
 @Id(keyType=KeyType.Auto) private Long id;
 private Long customerId;
 private Long revision;
 private Date cutoff;
 private String snapshotJson;
}
