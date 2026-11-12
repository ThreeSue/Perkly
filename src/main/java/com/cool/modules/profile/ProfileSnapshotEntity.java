package com.cool.modules.profile;
import com.mybatisflex.annotation.*;
import lombok.Data;
import java.util.Date;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 保存有来源依据和版本的用户画像快照
 * @Version: v1.1
 * @Date: 2026-11-10
 * @Time: 16:12:00
 */

@Data @Table("profile_snapshot") @org.dromara.autotable.annotation.Ignore
public class ProfileSnapshotEntity {
 @Id private Long customerId;
 private Long revision;
 private String evidenceJson;
 private String interpretationJson;
 private String modelVersion;
 private String promptVersion;
 private Date cutoff;
 private Date updatedAt;
}

