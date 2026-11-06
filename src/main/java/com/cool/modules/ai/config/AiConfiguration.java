package com.cool.modules.ai.config;
import com.cool.modules.ai.provider.BailianImageModel;
import com.cool.modules.ai.service.*;
import com.cool.modules.ai.entity.AiImageTaskEntity;
import com.cool.modules.ai.mapper.AiImageTaskMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.ai.image.ImageModel;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 初始化AI策略并以独立有界线程执行图片任务避免阻塞现有活动调度
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:48:45
 */
@Configuration
public class AiConfiguration {
    @Bean("couponCoverImageModel")
    public ImageModel imageModel(AiProperties p){return new BailianImageModel(p.getBaseUrl(),p.getApiKey(),p.getTimeoutSeconds());}
    @Bean public ApplicationRunner aiDefaults(AiSettingsService service){return args->service.initialize();}
    @Bean("aiImageExecutor") public ThreadPoolTaskExecutor executor(){
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);executor.setThreadNamePrefix("ai-image-");executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
    @Component
    @ConditionalOnProperty(name="perkly.ai.worker-enabled",havingValue="true",matchIfMissing=true)
    public static class Worker {
        private final AiTaskRunner runner;
        private final AiImageTaskMapper tasks;
        private final ThreadPoolTaskExecutor executor;
        public Worker(AiTaskRunner runner,AiImageTaskMapper tasks,
            @org.springframework.beans.factory.annotation.Qualifier("aiImageExecutor") ThreadPoolTaskExecutor executor){
            this.runner=runner;this.tasks=tasks;this.executor=executor;
        }
        @Scheduled(fixedDelayString="${perkly.ai.scan-interval-ms:3000}",initialDelay=20000)
        public void tick(){
            runner.reconcileStale();
            var queued=tasks.selectListByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getStatus,"QUEUED").orderBy(AiImageTaskEntity::getId,true).limit(10));
            for(var task:queued)dispatch(()->runner.run(task.getId()));
            var storing=tasks.selectListByQuery(QueryWrapper.create().in(AiImageTaskEntity::getStatus,List.of("GENERATED","STORAGE_FAILED"))
                .le(AiImageTaskEntity::getNextAttemptAt,new Date()).orderBy(AiImageTaskEntity::getId,true).limit(10));
            for(var task:storing)dispatch(()->runner.store(task.getId()));
        }
        private void dispatch(Runnable work){
            try{executor.execute(work);}catch(org.springframework.core.task.TaskRejectedException busy){/* 下次扫描继续，任务未认领 */}
        }
    }
}
