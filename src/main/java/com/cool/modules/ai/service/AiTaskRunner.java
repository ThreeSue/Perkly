package com.cool.modules.ai.service;
import com.cool.modules.ai.config.AiProperties;
import com.cool.modules.ai.entity.*;
import com.cool.modules.ai.mapper.*;
import com.cool.modules.ai.provider.BailianImageModel;
import com.cool.modules.space.entity.SpaceInfoEntity;
import com.cool.modules.space.mapper.SpaceInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.image.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 分阶段执行图片任务持久化云端结果并补偿素材保存且不重复付费生成
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:47:32
 */
@Service
public class AiTaskRunner {
    private final AiImageTaskMapper tasks;
    private final AiDailyUsageMapper usages;
    private final SpaceInfoMapper materials;
    private final AiSettingsService settings;
    private final AiTaskService taskService;
    private final AiFileStore files;
    private final ImageModel model;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper json;
    private final java.util.concurrent.atomic.AtomicLong recoveryCursor=new java.util.concurrent.atomic.AtomicLong();
    public AiTaskRunner(AiImageTaskMapper tasks,AiDailyUsageMapper usages,SpaceInfoMapper materials,AiSettingsService settings,
        AiTaskService taskService,AiFileStore files,@Qualifier("couponCoverImageModel") ImageModel model,
        PlatformTransactionManager transactions,ObjectMapper json) {
        this.tasks=tasks;this.usages=usages;this.materials=materials;this.settings=settings;this.taskService=taskService;
        this.files=files;this.model=model;this.transactions=transactions;this.json=json;
    }
    public record ProviderResult(String url,String base64,String requestId,String usage) {}
    private <T>T tx(java.util.function.Supplier<T> work) {
        var tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status->work.get());
    }
    private AiImageTaskEntity locked(Long id){return tasks.selectOneByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getId,id).forUpdate());}
    private AiDailyUsageEntity usage(AiImageTaskEntity task) {
        return usages.selectOneByQuery(QueryWrapper.create().eq(AiDailyUsageEntity::getMerchantId,task.getMerchantId())
            .eq(AiDailyUsageEntity::getDayKey,task.getQuotaDay()).forUpdate());
    }
    private String safeError(Exception e) {
        // 不保存HTTP原始异常正文、密钥、参考图或供应商临时签名URL。
        if(e instanceof BailianImageModel.ProviderFailure)return e.getMessage();
        if(e instanceof com.cool.core.exception.CoolException) {
            String message=Objects.toString(e.getMessage(),"图片处理失败");
            return message.substring(0,Math.min(500,message.length()));
        }
        return "图片处理失败（"+e.getClass().getSimpleName()+"），可查看状态后重试素材保存";
    }
    public void run(Long id) {
        var task=tx(()->{
            var row=locked(id);
            if(row==null || !"QUEUED".equals(row.getStatus()) || !Boolean.TRUE.equals(settings.current().getEnabled()))return null;
            row.setStatus("RUNNING");row.setStartedAt(new Date());tasks.update(row);return row;
        });
        if(task==null)return;
        long start=System.nanoTime();
        try {
            String reference=null;
            if(task.getParentMaterialId()!=null) {
                var parent=materials.selectOneById(task.getParentMaterialId());
                if(parent==null || !task.getMerchantId().equals(parent.getMerchantId()))throw new com.cool.core.exception.CoolException("参考图素材归属失效");
                reference=files.reference(parent);
            }
            var result=model.call(new ImagePrompt(task.getPrompt(),new BailianImageModel.Options(task.getModel(),reference)));
            var image=result.getResult().getOutput();
            var saved=new ProviderResult(image.getUrl(),image.getB64Json(),
                Objects.toString(result.getMetadata().get("providerRequestId"),""),Objects.toString(result.getMetadata().get("usage"),""));
            try{files.journal(task.getImageKey(),taskService.encode(saved));}catch(java.io.IOException ignored){/* 数据库仍作为首要持久化路径 */}
            tx(()->{
                var row=locked(id);
                if(!Set.of("RUNNING","UNKNOWN").contains(row.getStatus()))return null;
                var usage=usage(row);
                if("UNKNOWN".equals(row.getStatus()))usage.setUncertainCount(Math.max(0,usage.getUncertainCount()-1));
                usage.setSuccessCount(usage.getSuccessCount()+1);usages.update(usage);
                row.setProviderResult(taskService.encode(saved));row.setProviderRequestId(saved.requestId());
                row.setDurationMs((System.nanoTime()-start)/1_000_000);row.setImageCount(1);
                row.setStatus("GENERATED");row.setNextAttemptAt(new Date());row.setErrorMessage("");tasks.update(row);return null;
            });
            store(id);
        } catch(Exception failure) {
            // 已持久化的云端结果只进入保存补偿，绝不重发生成请求。
            var current=tasks.selectOneById(id);
            if(current!=null && Set.of("GENERATED","STORING","STORAGE_FAILED","STORED").contains(current.getStatus()))return;
            tx(()->{
                var row=locked(id);if(row==null || !Set.of("RUNNING","UNKNOWN").contains(row.getStatus()))return null;
                boolean uncertain=!(failure instanceof com.cool.core.exception.CoolException)
                    && (!(failure instanceof BailianImageModel.ProviderFailure p) || p.uncertain());
                var usage=usage(row);
                if("UNKNOWN".equals(row.getStatus()))usage.setUncertainCount(Math.max(0,usage.getUncertainCount()-1));
                if(uncertain)usage.setUncertainCount(usage.getUncertainCount()+1);else usage.setFailedCount(usage.getFailedCount()+1);
                usages.update(usage);row.setStatus(uncertain?"UNKNOWN":"FAILED");row.setErrorMessage(safeError(failure));
                row.setDurationMs((System.nanoTime()-start)/1_000_000);row.setCompletedAt(new Date());tasks.update(row);return null;
            });
        }
    }
    public void store(Long id) {
        var task=tx(()->{
            var row=locked(id);
            if(row==null || !Set.of("GENERATED","STORAGE_FAILED").contains(row.getStatus()))return null;
            row.setStatus("STORING");row.setNextAttemptAt(new Date(System.currentTimeMillis()+600000));tasks.update(row);return row;
        });
        if(task==null)return;
        try {
            var provider=json.readValue(task.getProviderResult(),ProviderResult.class);
            var result=files.store(task,provider.url(),provider.base64());
            tx(()->{
                var row=locked(id);
                if("STORED".equals(row.getStatus()))return null;
                var material=materials.selectOneByQuery(QueryWrapper.create().eq(SpaceInfoEntity::getAiTaskId,id));
                if(material==null) {
                    material=new SpaceInfoEntity();material.setMerchantId(task.getMerchantId());material.setUploaderId(task.getActorId());
                    material.setSource("AI");material.setAiTaskId(id);material.setParentMaterialId(task.getParentMaterialId());
                    material.setMaterialDeleted(false);material.setUrl(result.url());material.setFilePath(result.relativePath());
                    material.setFileId(result.fileId());material.setName("AI封面-"+task.getId()+".png");material.setType("image");material.setSize(result.size());
                    materials.insert(material);
                }
                row.setMaterialId(material.getId());row.setStatus("STORED");row.setCompletedAt(new Date());row.setErrorMessage("");
                tasks.update(row);return null;
            });
            try{files.clearJournal(task.getImageKey());}catch(java.io.IOException ignored){/* 已入库，残留恢复文件可后续清理 */}
        } catch(Exception failure) {
            tx(()->{
                var row=locked(id);if(row==null || "STORED".equals(row.getStatus()))return null;
                row.setStatus("STORAGE_FAILED");row.setErrorMessage("云端已生成，素材保存待重试："+safeError(failure));
                row.setNextAttemptAt(new Date(System.currentTimeMillis()+60000));tasks.update(row);return null;
            });
        }
    }
    public void reconcileStale() {
        var recoverable=tasks.selectListByQuery(QueryWrapper.create().in(AiImageTaskEntity::getStatus,List.of("RUNNING","UNKNOWN"))
            .gt(AiImageTaskEntity::getId,recoveryCursor.get()).orderBy(AiImageTaskEntity::getId,true).limit(100));
        if(recoverable.isEmpty())recoveryCursor.set(0);
        for(var candidate:recoverable) {
            recoveryCursor.set(candidate.getId());
            try {
                String recovered=files.recovery(candidate.getImageKey());
                if(recovered!=null) {
                    var known=json.readValue(recovered,ProviderResult.class);
                    tx(()->{
                        var row=locked(candidate.getId());
                        if(row==null || !Set.of("RUNNING","UNKNOWN").contains(row.getStatus()))return null;
                        var usage=usage(row);
                        if("UNKNOWN".equals(row.getStatus()))usage.setUncertainCount(Math.max(0,usage.getUncertainCount()-1));
                        usage.setSuccessCount(usage.getSuccessCount()+1);usages.update(usage);
                        row.setProviderResult(recovered);row.setProviderRequestId(known.requestId());row.setImageCount(1);
                        row.setStatus("GENERATED");row.setNextAttemptAt(new Date());row.setErrorMessage("");tasks.update(row);return null;
                    });
                }
            } catch(java.io.IOException ignored){/* 损坏记录不触发云端重试，仍保留UNKNOWN待核查 */}
        }
        var cutoff=new Date(System.currentTimeMillis()-600000);
        for(var task:tasks.selectListByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getStatus,"RUNNING").lt(AiImageTaskEntity::getStartedAt,cutoff).limit(100))) {
            tx(()->{
                var row=locked(task.getId());
                if(row==null || !"RUNNING".equals(row.getStatus()) || !row.getStartedAt().before(cutoff))return null;
                row.setStatus("UNKNOWN");row.setErrorMessage("生成进程中断或超时，云端结果未确定；不会自动再次生成");
                var usage=usage(row);usage.setUncertainCount(usage.getUncertainCount()+1);usages.update(usage);tasks.update(row);return null;
            });
        }
        for(var task:tasks.selectListByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getStatus,"STORING").lt(AiImageTaskEntity::getNextAttemptAt,new Date()).limit(100))) {
            tx(()->{
                var row=locked(task.getId());if(row!=null && "STORING".equals(row.getStatus())) {
                    row.setStatus("STORAGE_FAILED");row.setErrorMessage("素材保存中断，将重试保存已有云端结果");row.setNextAttemptAt(new Date());tasks.update(row);
                }return null;
            });
        }
    }
}
