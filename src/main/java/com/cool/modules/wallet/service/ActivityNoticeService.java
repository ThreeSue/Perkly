package com.cool.modules.wallet.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.mapper.*;
import com.cool.modules.workspace.entity.WorkspaceNoticeRecipientEntity;
import com.cool.modules.workspace.mapper.WorkspaceNoticeRecipientMapper;
import com.cool.modules.workspace.service.WorkspaceNoticeService;
import com.mybatisflex.core.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import static com.cool.modules.wallet.service.ActivityAudienceService.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 通过持久化事件和账号级投递任务可靠发送活动站内通知
 * @Version: v1.1
 * @Date: 2026-11-05
 * @Time: 21:24:12
 */
@Service @RequiredArgsConstructor @Slf4j
public class ActivityNoticeService {
    private final WalletActivityEventMapper events;
    private final WalletActivityDeliveryMapper deliveries;
    private final WalletActivityMapper activities;
    private final ActivityAudienceService audience;
    private final WorkspaceNoticeService notices;
    private final WorkspaceNoticeRecipientMapper receipts;
    private final BaseSysUserMapper users;
    private final SupportAccess access;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper json;

    public record Snapshot(String title,String description,String rate,Date startAt,Date endAt,List<StoreTarget> stores,
                           String state,boolean enabled,List<Long> participatingStoreIds) {}
    public record DeliveryView(Long id,Long recipientId,String name,String status,Integer attempts,
                               String lastError,Long noticeId,Date readAt) {}

    private Date immediate() {
        // 项目日期列为秒精度，向下取整避免“立即重试”被数据库舍入到未来一秒。
        return new Date(System.currentTimeMillis()/1000*1000);
    }

    public void published(WalletActivityEntity activity) {
        record(activity,"PUBLISHED",audience.participating(activity),new Date());
        lifecycle(activity);
    }

    public void changed(WalletActivityEntity activity,List<StoreTarget> affected,String kind) {
        record(activity,kind,affected,new Date());
        lifecycle(activity);
    }

    private void lifecycle(WalletActivityEntity activity) {
        if(!"PUBLISHED".equals(activity.getStatus()))return;
        Date now=new Date();
        var targets=audience.participating(activity);
        if(activity.getStartAt().after(now)) {
            long lead=Math.min(24*60*60*1000L,Math.max(1,(activity.getStartAt().getTime()-now.getTime())/2));
            record(activity,"REMINDER",targets,new Date(activity.getStartAt().getTime()-lead));
        }
        record(activity,"ENDED",targets,activity.getEndAt());
    }

    private void record(WalletActivityEntity activity,String kind,List<StoreTarget> stores,Date due) {
        if(!due.after(new Date()))due=immediate();
        int version=audience.version(activity);
        if(events.selectCountByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,activity.getId())
            .eq(WalletActivityEventEntity::getActivityVersion,version).eq(WalletActivityEventEntity::getKind,kind))>0)return;
        var event=new WalletActivityEventEntity();
        event.setActivityId(activity.getId());event.setActivityVersion(version);event.setKind(kind);
        event.setDueAt(due);event.setNextAttemptAt(due);event.setAttempts(0);event.setStatus("PENDING");
        try {
            event.setPayload(json.writeValueAsString(new Snapshot(activity.getTitle(),activity.getDescription(),
                "STAMP".equals(activity.getKind())?"跨商家消费集章":activity.getDiscountRate()==null?"待设定":activity.getDiscountRate().toPlainString(),
                activity.getStartAt(),activity.getEndAt(),stores,activity.getStatus(),!Boolean.FALSE.equals(activity.getEnabled()),
                audience.participating(activity).stream().map(StoreTarget::id).toList())));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new CoolException("活动通知事件无法保存"); }
        events.insert(event);
    }

    private Snapshot snapshot(WalletActivityEventEntity event) {
        try { return json.readValue(event.getPayload(),Snapshot.class); }
        catch(java.io.IOException e) { throw new CoolException("活动通知快照无效"); }
    }

    private void transaction(Runnable work) {
        var template=new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.executeWithoutResult(status->work.run());
    }

    private boolean superseded(WalletActivityEventEntity event) {
        if(!Set.of("REMINDER","ENDED").contains(event.getKind()))return false;
        var current=activities.selectOneById(event.getActivityId());
        if(current==null || !"PUBLISHED".equals(current.getStatus())
            || audience.version(current)!=event.getActivityVersion())return true;
        return "REMINDER".equals(event.getKind()) &&
            (Boolean.FALSE.equals(current.getEnabled()) || !current.getStartAt().after(new Date()));
    }

    private void fanout(Long id) {
        var event=events.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getId,id).forUpdate());
        if(event==null || !"PENDING".equals(event.getStatus()) || event.getNextAttemptAt().after(new Date())
            || event.getDueAt().after(new Date()))return;
        if(superseded(event)) {
            event.setStatus("SKIPPED");events.update(event);return;
        }
        var selected=audience.recipients(snapshot(event).stores());
        for(var recipient:selected.recipients()) {
            var row=new WalletActivityDeliveryEntity();
            row.setEventId(id);row.setRecipientId(recipient.id());row.setMerchantId(recipient.merchantId());
            row.setStoreId(recipient.storeId());row.setStatus("PENDING");row.setAttempts(0);row.setNextAttemptAt(immediate());
            deliveries.insert(row);
        }
        event.setGaps(String.join("\n",selected.gaps()));
        event.setStatus(selected.recipients().isEmpty()?"DONE":"READY");
        events.update(event);
    }

    private String label(String kind) {
        return switch(kind) {
            case "PUBLISHED" -> "活动发布";
            case "INVITED" -> "集章活动邀请，请确认参与门店";
            case "INVITATION_WITHDRAWN" -> "集章邀请已撤回，原确认不再有效";
            case "REMINDER" -> "活动开始前提醒";
            case "ENDED","CLOSED" -> "活动结束";
            case "PARTICIPATION" -> "活动参与范围调整";
            default -> "活动重要调整";
        };
    }
    private String time(Date value) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(java.time.ZoneId.of("Asia/Shanghai")).format(value.toInstant());
    }
    private String excerpt(String text,int limit) {
        return text.length()<=limit ? text : text.substring(0,limit)+"…完整内容见详情";
    }

    private void deliver(Long id) {
        var delivery=deliveries.selectOneByQuery(QueryWrapper.create().eq(WalletActivityDeliveryEntity::getId,id).forUpdate());
        if(delivery==null || !Set.of("PENDING","FAILED").contains(delivery.getStatus())
            || delivery.getNextAttemptAt().after(new Date()))return;
        var event=events.selectOneById(delivery.getEventId());
        users.selectOneByQuery(QueryWrapper.create().eq(com.cool.modules.base.entity.sys.BaseSysUserEntity::getId,delivery.getRecipientId()).forUpdate());
        if(superseded(event) || !audience.valid(new Recipient(delivery.getRecipientId(),delivery.getMerchantId(),delivery.getStoreId()))) {
            delivery.setStatus("SKIPPED");deliveries.update(delivery);return;
        }
        var data=snapshot(event);
        var visible=audience.recipientStores(new Recipient(delivery.getRecipientId(),delivery.getMerchantId(),delivery.getStoreId()),data.stores());
        if(visible.isEmpty()){delivery.setStatus("SKIPPED");deliveries.update(delivery);return;}
        var included=data.participatingStoreIds()==null?visible.stream().map(StoreTarget::id).toList():data.participatingStoreIds();
        String storeNames=visible.stream().filter(s->included.contains(s.id()))
            .map(StoreTarget::name).collect(java.util.stream.Collectors.joining("、"));
        if(storeNames.isEmpty())storeNames="无";
        String excluded=visible.stream().filter(s->!included.contains(s.id()))
            .map(StoreTarget::name).reduce((a,b)->a+"、"+b).orElse("无");
        String rule="跨商家消费集章".equals(data.rate())?"\n跨商家消费集章：完成到店消费，同一商家只计一章，达标发奖励券。":"\n储值支付 "+data.rate()+" 折（券后余额适用，活动取最大单项）";
        String content=data.title()+"\n规则："+excerpt(Objects.toString(data.description(),""),1800)+rule
            +"\n北京时间："+time(data.startAt())+" 至 "+time(data.endAt())+"（结束不含）\n本次涉及的参与门店："+excerpt(storeNames,1100)
            +"\n已排除或退出的门店："+excerpt(excluded,1100)
            +"\n本次事件状态："+("INVITED".equals(event.getKind())?"等待商家确认，尚未上线":"INVITATION_WITHDRAWN".equals(event.getKind())?"邀请已撤回":Set.of("ENDED","CLOSED").contains(event.getKind())?"已结束":data.enabled()?"启用":"暂停")
            +"\n"+label(event.getKind())+("跨商家消费集章".equals(data.rate())?"。请打开详情核对；全部商家确认后上线，不能单独退出已上线活动。":"。参与与退出以详情中的当前设置为准。");
        Long notice=notices.activityNotice(event.getId(),event.getActivityId(),event.getActivityVersion(),
            delivery.getRecipientId(),label(event.getKind())+" · "+data.title(),content);
        delivery.setNoticeId(notice);delivery.setStatus("SENT");delivery.setAttempts(delivery.getAttempts()+1);
        delivery.setLastError("");deliveries.update(delivery);
    }

    private String error(Exception failure) {
        String text=failure.getClass().getSimpleName()+": "+Objects.toString(failure.getMessage(),"投递失败");
        return text.substring(0,Math.min(500,text.length()));
    }

    private Date next(int attempt) {
        return new Date(System.currentTimeMillis()+Math.min(3600000L,60000L*Math.max(1,attempt)));
    }

    /** 复用项目已有 Spring 调度基础设施，也供测试显式驱动。 */
    public void tick() {
        Date now=new Date();
        var due=events.selectListByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getStatus,"PENDING")
            .le(WalletActivityEventEntity::getNextAttemptAt,now).orderBy(WalletActivityEventEntity::getId,true).limit(30));
        for(var row:due) {
            try { transaction(()->fanout(row.getId())); }
            catch(Exception failure) {
                log.warn("活动事件 {} 待重试",row.getId(),failure);
                transaction(()->{
                    var event=events.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getId,row.getId()).forUpdate());
                    event.setAttempts(event.getAttempts()+1);event.setLastError(error(failure));
                    event.setNextAttemptAt(next(event.getAttempts()));events.update(event);
                });
            }
        }
        var pending=deliveries.selectListByQuery(QueryWrapper.create().in(WalletActivityDeliveryEntity::getStatus,List.of("PENDING","FAILED"))
            .le(WalletActivityDeliveryEntity::getNextAttemptAt,new Date()).orderBy(WalletActivityDeliveryEntity::getId,true).limit(100));
        for(var row:pending) {
            try { transaction(()->deliver(row.getId())); }
            catch(Exception failure) {
                log.warn("活动投递 {} 待重试",row.getId(),failure);
                transaction(()->{
                    var delivery=deliveries.selectOneByQuery(QueryWrapper.create().eq(WalletActivityDeliveryEntity::getId,row.getId()).forUpdate());
                    if("SENT".equals(delivery.getStatus()))return;
                    delivery.setStatus("FAILED");delivery.setAttempts(delivery.getAttempts()+1);
                    delivery.setLastError(error(failure));delivery.setNextAttemptAt(next(delivery.getAttempts()));deliveries.update(delivery);
                });
            }
        }
        var candidates=new LinkedHashSet<>(pending.stream().map(WalletActivityDeliveryEntity::getEventId).toList());
        events.selectListByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getStatus,"READY").limit(100))
            .forEach(event->candidates.add(event.getId()));
        for(Long eventId:candidates) {
            transaction(()->{
                var locked=events.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getId,eventId).forUpdate());
                if(deliveries.selectCountByQuery(QueryWrapper.create().eq(WalletActivityDeliveryEntity::getEventId,eventId)
                    .in(WalletActivityDeliveryEntity::getStatus,List.of("PENDING","FAILED")))==0) {
                    locked.setStatus("DONE");events.update(locked);
                }
            });
        }
    }

    public List<WalletActivityEventEntity> events(Long activityId) {
        eventPermission(activityId);
        return events.selectListByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getActivityId,activityId)
            .orderBy(WalletActivityEventEntity::getId,false));
    }

    public List<DeliveryView> deliveries(Long eventId) {
        var event=events.selectOneById(eventId);
        if(event==null)throw new CoolException("通知事件不存在",404);
        eventPermission(event.getActivityId());
        return deliveries.selectListByQuery(QueryWrapper.create().eq(WalletActivityDeliveryEntity::getEventId,eventId)).stream().map(d->{
            var user=users.selectOneById(d.getRecipientId());
            var receipt=d.getNoticeId()==null?null:receipts.selectOneByQuery(QueryWrapper.create()
                .eq(WorkspaceNoticeRecipientEntity::getNoticeId,d.getNoticeId()).eq(WorkspaceNoticeRecipientEntity::getRecipientId,d.getRecipientId()));
            return new DeliveryView(d.getId(),d.getRecipientId(),user==null?"历史账号":user.getName(),d.getStatus(),
                d.getAttempts(),d.getLastError(),d.getNoticeId(),receipt==null?null:receipt.getReadAt());
        }).toList();
    }

    @Transactional(rollbackFor=Exception.class)
    public void retry(Long eventId) {
        var event=events.selectOneByQuery(QueryWrapper.create().eq(WalletActivityEventEntity::getId,eventId).forUpdate());
        if(event==null)throw new CoolException("通知事件不存在",404);
        eventPermission(event.getActivityId());
        if("PENDING".equals(event.getStatus())){
            event.setNextAttemptAt(new Date(Math.max(immediate().getTime(),event.getDueAt().getTime())));
            events.update(event);
        }
        for(var row:deliveries.selectListByQuery(QueryWrapper.create().eq(WalletActivityDeliveryEntity::getEventId,eventId)
            .eq(WalletActivityDeliveryEntity::getStatus,"FAILED").forUpdate())) {
            row.setNextAttemptAt(immediate());deliveries.update(row);
        }
    }
    private void eventPermission(Long activityId) {
        var activity=activities.selectOneById(activityId);
        if(activity==null)throw new CoolException("活动不存在",404);
        access.platform("STAMP".equals(activity.getKind())?"stamp:manage":"activity:manage");
    }
}
