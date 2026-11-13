package com.cool.modules.ai.controller;
import com.cool.core.request.R;
import com.cool.modules.ai.service.*;
import com.cool.modules.ai.entity.*;
import com.cool.modules.ai.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.support.service.UserFeedbackService.PageResult;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营AI开关模型额度任务用量和明确标注的费用估算监管
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:52:25
 */
@RestController @RequiredArgsConstructor @RequestMapping("/admin/ai/console")
public class AiConsoleController {
    private final AiSettingsService settings;
 private final AiFeatureControl features;
 @GetMapping("/features") public R<?> features(){return R.ok(features.list());}
 @PostMapping("/features") public R<?> features(@RequestBody AiFeatureControl.Policy input){features.save(input);return R.ok();}
    private final AiTaskService tasks;
    private final AiDailyUsageMapper usages;
    private final AiImageTaskMapper taskRows;
    private final AiAuditMapper audits;
    private final MerchantInfoMapper merchants;
    private final com.cool.modules.base.mapper.sys.BaseSysUserMapper users;
    private final SupportAccess access;
    @GetMapping("/settings") public R<AiSettingsService.SettingsView> settings(){return R.ok(settings.view());}
    @PostMapping("/settings") public R<Void> settings(@RequestBody AiSettingsService.SettingsInput input){settings.update(input);return R.ok();}
    @GetMapping("/policies") public R<List<AiSettingsService.PolicyView>> policies(){return R.ok(settings.policies());}
    @PostMapping("/policy") public R<Void> policy(@RequestBody AiSettingsService.PolicyInput input){settings.policy(input);return R.ok();}
    @GetMapping("/tasks") public R<PageResult<AiTaskService.TaskView>> tasks(@RequestParam(required=false) Long merchantId,@RequestParam(required=false) String status,
        @RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){
        return R.ok(tasks.adminPage(merchantId,status,pageNumber,pageSize));
    }
    @io.swagger.v3.oas.annotations.media.Schema(name="AiImageDailyUsage")
    public record Usage(Long merchantId,String merchantName,String day,Integer requests,Integer success,Integer failed,Integer uncertain,
        BigDecimal estimatedCost,long unpricedImages) {}
    @GetMapping("/usage") public R<PageResult<Usage>> usage(@RequestParam(required=false) Long merchantId,
        @RequestParam(required=false) String startDay,@RequestParam(required=false) String endDay,
        @RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){
        access.platform("ai:manage");SupportAccess.page(pageNumber,pageSize);
        LocalDate end,start;
        try {
            end=endDay==null?LocalDate.parse(AiTaskService.day()):LocalDate.parse(endDay);
            start=startDay==null?end.minusDays(6):LocalDate.parse(startDay);
        }catch(java.time.format.DateTimeParseException invalid){throw new com.cool.core.exception.CoolException("日期须为yyyy-MM-dd");}
        if(start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start,end)>30)throw new com.cool.core.exception.CoolException("一次最多查看31天用量");
        var q=QueryWrapper.create().ge(AiDailyUsageEntity::getDayKey,start.toString()).le(AiDailyUsageEntity::getDayKey,end.toString());
        if(merchantId!=null)q.eq(AiDailyUsageEntity::getMerchantId,merchantId);
        var rows=usages.paginate(pageNumber,pageSize,q.orderBy(AiDailyUsageEntity::getDayKey,false).orderBy(AiDailyUsageEntity::getMerchantId,true));
        var list=rows.getRecords().stream().map(u->{
            var m=merchants.selectOneById(u.getMerchantId());
            var generated=taskRows.selectListByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getMerchantId,u.getMerchantId())
                .eq(AiImageTaskEntity::getQuotaDay,u.getDayKey()).eq(AiImageTaskEntity::getImageCount,1));
            BigDecimal known=generated.stream().map(AiImageTaskEntity::getEstimatedCost).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
            long unpriced=generated.stream().filter(t->t.getEstimatedCost()==null).count();
            return new Usage(u.getMerchantId(),m==null?"历史商家":m.getName(),u.getDayKey(),u.getReservedCount(),u.getSuccessCount(),
                u.getFailedCount(),u.getUncertainCount(),known,unpriced);
        }).toList();
        return R.ok(new PageResult<>(list,rows.getTotalRow(),pageNumber,pageSize));
    }
    @io.swagger.v3.oas.annotations.media.Schema(name="AiAuditView")
    public record AuditView(Long id,Long actorId,String actorName,Long merchantId,String merchantName,Long taskId,String action,String detail,Date createTime) {}
    @GetMapping("/audit") public R<PageResult<AuditView>> audit(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){
        access.platform("ai:manage");SupportAccess.page(pageNumber,pageSize);
        var rows=audits.paginate(pageNumber,pageSize,QueryWrapper.create().orderBy(AiAuditEntity::getId,false));
        var list=rows.getRecords().stream().map(row->{
            boolean usage=row.getAction()!=null && row.getAction().startsWith("USE_");
            var actor=usage || row.getActorId()==null?null:users.selectOneById(row.getActorId());
            var merchant=row.getMerchantId()==null?null:merchants.selectOneById(row.getMerchantId());
            return new AuditView(row.getId(),row.getActorId(),usage?"用量主体（按功能计额）":actor==null?"历史人员":Objects.toString(actor.getName(),actor.getUsername()),
                row.getMerchantId(),row.getMerchantId()==null?"全平台":merchant==null?"历史商家":merchant.getName(),
                row.getTaskId(),row.getAction(),row.getDetail(),row.getCreateTime());
        }).toList();
        return R.ok(new PageResult<>(list,rows.getTotalRow(),pageNumber,pageSize));
    }
}
