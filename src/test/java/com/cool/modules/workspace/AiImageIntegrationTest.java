package com.cool.modules.workspace;
import com.cool.modules.ai.entity.*;
import com.cool.modules.ai.mapper.*;
import com.cool.modules.ai.service.*;
import com.cool.modules.ai.config.AiProperties;
import com.cool.modules.ai.provider.BailianImageModel;
import com.cool.modules.space.mapper.SpaceInfoMapper;
import com.cool.modules.space.entity.SpaceInfoEntity;
import com.cool.core.exception.CoolException;
import com.cool.core.enums.UserTypeEnum;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.ai.image.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.api.*;
import java.util.*;
import java.nio.file.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 独立数据库验证AI权限额度并发幂等编辑入库补偿及历史恢复
 * @Version: v1.1
 * @Date: 2026-11-06
 * @Time: 16:58:22
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"perkly.ai.worker-enabled=false",
    "sms-auth.access-key-id=features-test-unused","sms-auth.access-key-secret=features-test-unused"})
class AiImageIntegrationTest extends FeatureModulesIntegrationTest {
    @Autowired AiTaskService ai;
    @Autowired AiAccessService aiAccess;
    @Autowired AiSettingsService aiSettings;
    @Autowired AiTaskRunner aiRunner;
    @Autowired AiProperties aiProperties;
    @Autowired AiImageTaskMapper aiRows;
    @Autowired AiDailyUsageMapper aiUsage;
    @Autowired AiMerchantPolicyMapper aiPolicies;
    @Autowired AiAuditMapper aiAudits;
    @Autowired SpaceInfoMapper aiMaterials;
    @MockBean(name="couponCoverImageModel") ImageModel cloud;
    @SpyBean AiFileStore aiFiles;
    String oldKey,oldPath,oldBase;
    Path testRoot;
    AiSettingsService.SettingsInput oldSettings;
    @BeforeEach void aiSetup()throws Exception {
        ops();
        var s=aiSettings.view();oldSettings=new AiSettingsService.SettingsInput(s.enabled(),s.allowedModels(),s.defaultDailyLimit(),s.estimatedUnitPrice());
        oldKey=aiProperties.getApiKey();aiProperties.setApiKey("test-only-not-a-live-credential");
        oldPath=localFiles.getUploadPath();oldBase=localFiles.getBaseUrl();
        testRoot=Path.of("logs/test/ai-images-"+prefix).toAbsolutePath().normalize();Files.createDirectories(testRoot);
        localFiles.setUploadPath(testRoot.toString());localFiles.setBaseUrl("http://localhost/test-ai-upload");
        for(String permission:List.of("ai:image:generate","material:view","coupon:create","coupon:update"))activityPermission(owner,permission);
        aiSettings.update(new AiSettingsService.SettingsInput(true,List.of("qwen-image-3.0"),5,new BigDecimal("0.1")));
    }
    @AfterEach void aiRestore(){aiProperties.setApiKey(oldKey);localFiles.setUploadPath(oldPath);localFiles.setBaseUrl(oldBase);}
    void ops(){
        var u=principal(admin,UserTypeEnum.ADMIN,"ai:manage","ai:image:generate","coupon:template:add","coupon:template:update",
            "feedback:manage","wallet:manage","activity:manage");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u,null,u.getAuthorities()));
    }
    AiAccessService.Draft draft(){return new AiAccessService.Draft(null,merchant,"测试代金券","CASH","实际已填表单",0,List.of(),
        List.of(),BigDecimal.ZERO,null,new BigDecimal("10"),null);}
    AiTaskService.Create create(String key){return new AiTaskService.Create(key,"GENERATE","qwen-image-3.0",null,null,draft());}
    byte[] png(int seed)throws Exception {
        var image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_RGB);var r=new Random(seed);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)image.setRGB(x,y,r.nextInt(0xffffff));
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();
    }
    ImageResponse result(int seed)throws Exception{return new ImageResponse(List.of(new ImageGeneration(new Image(null,Base64.getEncoder().encodeToString(png(seed))))));}
    @Test void aiPermissionContextAndRequiredActualContentsAreEnforced() {
        var prepared=aiAccess.prepare(owner,false,draft());assertTrue(prepared.suggestedPrompt().contains("不推测菜品"));
        assertThrows(CoolException.class,()->aiAccess.prepare(otherStaff,false,draft()));
        var foreign=merchants.selectOneById(merchant);foreign.setId(null);foreign.setName(prefix+"其他商家");merchants.insert(foreign);
        var wrong=new AiAccessService.Draft(null,foreign.getId(),"外店","CASH",null,0,List.of(),List.of(),null,null,null,null);
        assertThrows(CoolException.class,()->aiAccess.prepare(owner,false,wrong));
        var missing=new AiAccessService.Draft(null,merchant,"套餐","PACKAGE",null,0,List.of(),List.of(),null,null,null,null);
        assertThrows(CoolException.class,()->aiAccess.prepare(owner,false,missing));
        assertThrows(CoolException.class,()->ai.create(owner,false,new AiTaskService.Create("ai_bad_model","GENERATE","unapproved",null,null,draft())));
        verifyNoInteractions(cloud);
    }
    @Test void aiTurboIsSelectableOnlyAfterOperatorEnablesIt() {
        String turbo = "qwen-image-2.1-turbo";
        var request = new AiTaskService.Create("ai_turbo_select", "GENERATE", turbo, null, null, draft());
        assertTrue(aiSettings.view().supportedModels().contains(turbo));
        assertFalse(ai.quota(owner, false, merchant).models().contains(turbo));
        assertThrows(CoolException.class, () -> ai.create(owner, false, request));
        var models = List.of("qwen-image-3.0", turbo);
        aiSettings.update(new AiSettingsService.SettingsInput(true, models, 5, null));
        assertEquals(models, ai.quota(owner, false, merchant).models());
        assertEquals(turbo, ai.create(owner, false, request).model());
        assertEquals(1, ai.quota(owner, false, merchant).used());
    }
    @Test void aiIdempotencyFlagsLimitsAndSelectDoNotWriteCoupon() {
        var first=ai.create(owner,false,create("ai_same_request"));
        assertEquals(first.id(),ai.create(owner,false,create("ai_same_request")).id());
        assertEquals(1,ai.quota(owner,false,merchant).used());
        assertThrows(CoolException.class,()->ai.create(owner,false,new AiTaskService.Create("ai_same_request","GENERATE","qwen-image-3.0","不同的提示词",null,draft())));
        assertThrows(CoolException.class,()->ai.select(owner,false,merchant,null,first.id()));
        aiSettings.policy(new AiSettingsService.PolicyInput(merchant,false,1));
        assertThrows(CoolException.class,()->ai.create(owner,false,create("ai_disabled_merchant")));
        aiSettings.policy(new AiSettingsService.PolicyInput(merchant,true,0));
        assertThrows(CoolException.class,()->ai.create(owner,false,create("ai_zero_budget")));
        aiSettings.update(new AiSettingsService.SettingsInput(false,List.of("qwen-image-3.0"),5,null));
        assertThrows(CoolException.class,()->ai.create(owner,false,create("ai_global_disabled")));
        assertEquals(1,ai.quota(owner,false,merchant).used());verifyNoInteractions(cloud);
    }
    @Test void aiHttpPromptAndSchemasExcludeCredentialsAndProviderResults()throws Exception {
        var credential=token(admin,UserTypeEnum.ADMIN,"ai:manage","ai:image:generate","coupon:template:add");
        mvc.perform(get("/admin/ai/console/settings").header("Authorization",credential))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.keyConfigured").value(true))
            .andExpect(jsonPath("$.data.apiKey").doesNotExist());
        mvc.perform(post("/admin/ai/images/prompt").header("Authorization",credential).contentType("application/json")
            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(draft())))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.prompt").isString());
        mvc.perform(get("/v3/api-docs/admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.AiImageCreate.properties.draft").exists())
            .andExpect(jsonPath("$.components.schemas.AiCoverDraft.properties.merchantId").exists())
            .andExpect(jsonPath("$.components.schemas.AiImageTask.properties.materialId").exists())
            .andExpect(jsonPath("$.components.schemas.AiImageTask.properties.providerResult").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.AiSettingsView.properties.apiKey").doesNotExist());
    }
    @Test void aiGenerationEditingStorageRetryAndUnknownNeverRegenerate()throws Exception {
        aiSettings.policy(new AiSettingsService.PolicyInput(merchant,true,4));
        var first=ai.create(owner,false,create("ai_pipeline_first"));
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();org.springframework.test.context.transaction.TestTransaction.end();
        try {
            when(cloud.call(any(ImagePrompt.class))).thenReturn(result(1));
            aiRunner.run(first.id());aiRunner.run(first.id());
            var stored=ai.info(owner,false,merchant,first.id());assertEquals("STORED",stored.status());assertNotNull(stored.materialId());
            var material=aiMaterials.selectOneById(stored.materialId());assertEquals("AI",material.getSource());
            assertTrue(Files.exists(testRoot.resolve(material.getFilePath())));
            var edited=ai.create(owner,false,new AiTaskService.Create("ai_pipeline_edit","EDIT","qwen-image-3.0","保留原有内容，将背景改为淡绿色",first.id(),draft()));
            when(cloud.call(any(ImagePrompt.class))).thenReturn(result(2));aiRunner.run(edited.id());
            var editedView=ai.info(owner,false,merchant,edited.id());assertEquals("STORED",editedView.status());
            assertEquals(stored.materialId(),editedView.parentMaterialId());assertEquals(first.id(),editedView.parentTaskId());
            assertNotEquals(stored.url(),editedView.url());
            var failure=ai.create(owner,false,create("ai_pipeline_storage"));
            doThrow(new java.io.IOException("simulated storage failure")).when(aiFiles).store(any(),nullable(String.class),nullable(String.class));
            aiRunner.run(failure.id());assertEquals("STORAGE_FAILED",ai.info(owner,false,merchant,failure.id()).status());
            verify(cloud,times(3)).call(any(ImagePrompt.class));
            doCallRealMethod().when(aiFiles).store(any(),nullable(String.class),nullable(String.class));
            aiRunner.store(failure.id());aiRunner.store(failure.id());
            assertEquals("STORED",ai.info(owner,false,merchant,failure.id()).status());verify(cloud,times(3)).call(any(ImagePrompt.class));
            var unknown=ai.create(owner,false,create("ai_pipeline_unknown"));
            when(cloud.call(any(ImagePrompt.class))).thenThrow(new BailianImageModel.ProviderFailure("图片服务连接异常",true));
            aiRunner.run(unknown.id());aiRunner.run(unknown.id());
            assertEquals("UNKNOWN",ai.info(owner,false,merchant,unknown.id()).status());verify(cloud,times(4)).call(any(ImagePrompt.class));
            assertEquals(4,ai.quota(owner,false,merchant).used());
            assertThrows(CoolException.class,()->ai.create(owner,false,create("ai_budget_over")));
            assertEquals(first.id(),ai.create(owner,false,create("ai_pipeline_first")).id());
            var selected=ai.select(owner,false,merchant,null,first.id());assertEquals(stored.url(),selected.url());
            assertEquals(0,coupons.selectCountByQuery(QueryWrapper.create().eq(com.cool.modules.coupon.entity.CouponTemplateEntity::getMerchantId,merchant)),
                "选用图片不会直接保存优惠券或跳过审核");
            assertEquals(4,ai.page(owner,false,merchant,1,20).total());
        } finally{cleanupAiCommitted();}
    }
    @Test void aiQuotaConcurrentRequestsAndDuplicateReservationAreAtomic()throws Exception {
        aiSettings.policy(new AiSettingsService.PolicyInput(merchant,true,2));
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();org.springframework.test.context.transaction.TestTransaction.end();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->ai.create(owner,false,create("ai_concurrent_same")));
            var b=pool.submit(()->ai.create(owner,false,create("ai_concurrent_same")));
            assertEquals(a.get().id(),b.get().id());assertEquals(1,ai.quota(owner,false,merchant).used());
            java.util.concurrent.Callable<Boolean> one=()->{try{ai.create(owner,false,create(UUID.randomUUID().toString()));return true;}catch(CoolException denied){return false;}};
            var c=pool.submit(one);var d=pool.submit(one);assertNotEquals(c.get(),d.get());
            assertEquals(2,ai.quota(owner,false,merchant).used());
            assertEquals(2,aiRows.selectCountByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getMerchantId,merchant)));
        } finally{pool.shutdownNow();cleanupAiCommitted();}
    }
    @Test void aiRecoveryJournalRestoresKnownResultWithoutCloudCallAndKeepsArchivedHistory()throws Exception {
        var queued=ai.create(owner,false,create("ai_journal_recover"));
        var row=aiRows.selectOneById(queued.id());row.setStatus("RUNNING");row.setStartedAt(new Date(System.currentTimeMillis()-700000));aiRows.update(row);
        aiFiles.journal(row.getImageKey(),ai.encode(new AiTaskRunner.ProviderResult(null,Base64.getEncoder().encodeToString(png(5)),"mock-recovery","{}")));
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();org.springframework.test.context.transaction.TestTransaction.end();
        try {
            aiRunner.reconcileStale();assertEquals("GENERATED",ai.info(owner,false,merchant,row.getId()).status());
            aiRunner.store(row.getId());verifyNoInteractions(cloud);
            var result=ai.info(owner,false,merchant,row.getId());assertEquals("STORED",result.status());
            var material=aiMaterials.selectOneById(result.materialId());material.setMaterialDeleted(true);aiMaterials.update(material);
            assertTrue(ai.info(owner,false,merchant,row.getId()).archived());
            assertFalse(ai.select(owner,false,merchant,null,row.getId()).archived());
            assertEquals(1,ai.quota(owner,false,merchant).used());
        }finally{cleanupAiCommitted();}
    }
    @Test void aiReadAndSelectRejectForgedMerchantEvenForPlatformActor(){
        var task=ai.create(owner,false,create("ai_scope_own"));
        var foreign=merchants.selectOneById(merchant);foreign.setId(null);foreign.setName(prefix+"其他商家");merchants.insert(foreign);
        assertThrows(CoolException.class,()->ai.info(admin,true,foreign.getId(),task.id()));
        assertThrows(CoolException.class,()->ai.select(admin,true,foreign.getId(),null,task.id()));
        assertEquals("QUEUED",ai.info(owner,false,merchant,task.id()).status());verifyNoInteractions(cloud);
    }
    @Test void aiMaterialSourceFilteringAndPlatformTargetMerchantStayIsolated()throws Exception {
        for(String source:List.of("AI","UPLOAD","LEGACY","PLATFORM")){
            var row=new SpaceInfoEntity();row.setName(prefix+source);row.setUrl("http://localhost/test/"+source+".png");
            row.setType("image");row.setMaterialDeleted(false);row.setMerchantId("PLATFORM".equals(source)?null:merchant);
            if(!Set.of("LEGACY","PLATFORM").contains(source))row.setSource(source);aiMaterials.insert(row);
        }
        assertEquals(3,ai.materials(admin,true,merchant,1,20,null,null,"ALL").total());
        assertEquals(2,ai.materials(owner,false,merchant,1,20,null,null,"UPLOAD").total());
        assertEquals(1,ai.materials(owner,false,merchant,1,20,null,null,"AI").total());
        assertThrows(CoolException.class,()->ai.materials(otherStaff,false,merchant,1,20,null,null,"AI"));
        var credential=token(admin,UserTypeEnum.ADMIN,"ai:image:generate","coupon:template:add");
        mvc.perform(get("/admin/ai/images/materials").param("merchantId",merchant.toString()).param("source","AI").header("Authorization",credential))
            .andExpect(jsonPath("$.code").value(1000)).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list[0].source").value("AI"));
    }
    @Test void aiDifferentMerchantsCanReserveTheirFirstDailyBucketsConcurrently()throws Exception {
        var foreign=merchants.selectOneById(merchant);foreign.setId(null);foreign.setName(prefix+"并发其他商家");merchants.insert(foreign);
        var branch=stores.selectOneById(store);branch.setId(null);branch.setMerchantId(foreign.getId());stores.insert(branch);
        var foreignDraft=new AiAccessService.Draft(null,foreign.getId(),"其他商家代金券","CASH",null,0,List.of(),List.of(),null,null,null,null);
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();org.springframework.test.context.transaction.TestTransaction.end();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            for(int round=0;round<4;round++){
                var barrier=new java.util.concurrent.CyclicBarrier(2);
                java.util.function.Function<AiAccessService.Draft,java.util.concurrent.Callable<AiTaskService.TaskView>> submit=d->()->{
                    ops();try{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);return ai.create(admin,true,
                        new AiTaskService.Create(UUID.randomUUID().toString(),"GENERATE","qwen-image-3.0",null,null,d));
                    }finally{SecurityContextHolder.clearContext();}
                };
                var first=pool.submit(submit.apply(draft()));var second=pool.submit(submit.apply(foreignDraft));
                assertEquals(merchant,first.get(20,java.util.concurrent.TimeUnit.SECONDS).merchantId());
                assertEquals(foreign.getId(),second.get(20,java.util.concurrent.TimeUnit.SECONDS).merchantId());
            }
            ops();assertEquals(4,ai.quota(admin,true,foreign.getId()).used());assertEquals(4,ai.quota(owner,false,merchant).used());
        }finally{
            pool.shutdownNow();
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
                aiRows.deleteByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getMerchantId,foreign.getId()));
                aiUsage.deleteByQuery(QueryWrapper.create().eq(AiDailyUsageEntity::getMerchantId,foreign.getId()));
                stores.deleteById(branch.getId());merchants.deleteById(foreign.getId());
            });
            cleanupAiCommitted();
        }
    }
    void cleanupAiCommitted(){
        ops();
        var rows=aiRows.selectListByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getMerchantId,merchant));
        for(var row:rows){try{aiFiles.clearJournal(row.getImageKey());}catch(Exception ignored){}}
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx->{
            aiMaterials.deleteByQuery(QueryWrapper.create().eq(SpaceInfoEntity::getMerchantId,merchant).eq(SpaceInfoEntity::getSource,"AI"));
            aiRows.deleteByQuery(QueryWrapper.create().eq(AiImageTaskEntity::getMerchantId,merchant));
            aiUsage.deleteByQuery(QueryWrapper.create().eq(AiDailyUsageEntity::getMerchantId,merchant));
            aiPolicies.deleteByQuery(QueryWrapper.create().eq(AiMerchantPolicyEntity::getMerchantId,merchant));
            aiSettings.update(oldSettings);
            aiAudits.deleteByQuery(QueryWrapper.create().eq(AiAuditEntity::getActorId,admin));
        });
        cleanupActivityNoticeFixtures(admin);
    }
}
