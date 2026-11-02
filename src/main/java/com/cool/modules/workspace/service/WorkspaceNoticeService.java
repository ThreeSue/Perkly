package com.cool.modules.workspace.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.workspace.entity.*;
import com.cool.modules.workspace.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 按实时授权发送下级通知并隔离每个账号的阅读状态
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:24:10
 */
@Service @RequiredArgsConstructor
public class WorkspaceNoticeService {
    private final WorkspaceAuthorization auth;
    private final BaseSysUserMapper users;
    private final WorkspaceNoticeMapper notices;
    private final WorkspaceNoticeRecipientMapper recipients;

    public record SendInput(String requestId, String title, String content, List<Long> recipientIds) {}
    public record RecipientOption(Long id, String name, String username, String accountType,
                                  Long merchantId, String merchantName, Long storeId, String storeName, String roleName) {}
    public record NoticeView(Long id, String title, String content, String senderName, Date createTime, Date readAt) {}
    public record PageResult<T>(List<T> list, long total, int pageNumber, int pageSize) {}
    public record Receipt(Long recipientId, String name, Date readAt) {}

    private BaseSysUserEntity current(Long id) {
        var u = id == null ? null : users.selectOneById(id);
        if (u == null || !Integer.valueOf(1).equals(u.getStatus())) throw new CoolException("账号已失效",403);
        if (!"PLATFORM".equals(WorkspaceAuthorization.type(u))) auth.context(id);
        return u;
    }
    private NoticeRecipientPolicy.Sender sender(Long id) {
        var u = current(id);
        String type = WorkspaceAuthorization.type(u);
        if ("PLATFORM".equals(type)) {
            if (com.cool.core.util.CoolSecurityUtil.getCurrentUserType()!=com.cool.core.enums.UserTypeEnum.ADMIN
                || !Objects.equals(id,com.cool.core.util.CoolSecurityUtil.getCurrentUserId())) throw new CoolException("请使用运营身份",403);
            var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (!com.cool.core.util.CoolSecurityUtil.isSuperAdmin() && authentication.getAuthorities().stream()
                .noneMatch(a->"notice:send".equals(a.getAuthority()))) throw new CoolException("没有发送通知权限",403);
            return new NoticeRecipientPolicy.Sender(id,type,null,null,Set.of(),true,false);
        }
        auth.require(id,"notice:send");
        // 自定义管理岗位以授予的发送权限判定，不以历史 STAFF 字段否定已授权岗位。
        boolean ordinary = "ws_staff".equals(auth.role(u).getLabel());
        return new NoticeRecipientPolicy.Sender(id,type,u.getMerchantId(),"STORE".equals(type)?auth.boundStore(u).getId():null,
            new HashSet<>(auth.storeIds(u)),true,ordinary);
    }
    private RecipientOption allowed(NoticeRecipientPolicy.Sender sender, Long id) {
        try {
            var u=auth.user(id);var c=auth.context(id);
            // 门店发送只以普通员工为下级；自定义管理岗位不能被当作普通员工。
            String roleLabel=auth.role(u).getLabel();
            boolean ordinary="ws_staff".equals(roleLabel) || (!"ws_manager".equals(roleLabel) && !auth.permissions(id).contains("notice:send"));
            if (!NoticeRecipientPolicy.canSend(sender,new NoticeRecipientPolicy.Recipient(id,c.accountType(),u.getMerchantId(),c.storeId(),true,ordinary))) return null;
            return new RecipientOption(id,u.getName(),u.getUsername(),c.accountType(),u.getMerchantId(),c.merchantName(),c.storeId(),c.storeName(),c.roleName());
        } catch (CoolException invalid) { return null; }
    }
    public PageResult<RecipientOption> options(Long id, Long merchantId, Long storeId, int page, int size) {
        bounds(page,size);var sender=sender(id);
        var query=QueryWrapper.create().eq(BaseSysUserEntity::getStatus,1).isNotNull(BaseSysUserEntity::getMerchantId);
        if (!"PLATFORM".equals(sender.type())) query.eq(BaseSysUserEntity::getMerchantId,sender.merchantId());
        if (merchantId!=null) query.eq(BaseSysUserEntity::getMerchantId,merchantId);
        // 授权后分页，避免返回不可选账号，也不暴露手机号等敏感资料。
        var list=users.selectListByQuery(query.orderBy(BaseSysUserEntity::getId,true)).stream()
            .map(u->allowed(sender,u.getId())).filter(Objects::nonNull)
            .filter(u->storeId==null || storeId.equals(u.storeId())).toList();
        int start=(int)Math.min((long)(page-1)*size,list.size());
        return new PageResult<>(list.subList(start,Math.min(start+size,list.size())),list.size(),page,size);
    }
    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long send(Long id, SendInput input) {
        if(input==null || input.requestId()==null || !input.requestId().matches("[A-Za-z0-9_-]{8,64}")
            || input.title()==null || input.title().isBlank() || input.title().length()>100
            || input.content()==null || input.content().isBlank() || input.content().length()>5000
            || input.recipientIds()==null || input.recipientIds().isEmpty() || input.recipientIds().size()>100
            || input.recipientIds().stream().anyMatch(x->x==null || x<=0)) throw new CoolException("通知参数无效：标题100字、正文5000字、接收人1至100人");
        // 锁定发送账号使同账号并发重试串行，数据库唯一索引作最后保障。
        users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId,id).forUpdate());
        var sender=sender(id);
        var ids=new TreeSet<>(input.recipientIds());
        if(ids.stream().anyMatch(target->allowed(sender,target)==null)) throw new CoolException("包含无权通知或已失效的接收账号",403);
        var existing=notices.selectOneByQuery(QueryWrapper.create().eq(WorkspaceNoticeEntity::getSenderId,id).eq(WorkspaceNoticeEntity::getRequestId,input.requestId()));
        if(existing!=null) {
            var previous=recipients.selectListByQuery(QueryWrapper.create().eq(WorkspaceNoticeRecipientEntity::getNoticeId,existing.getId()))
                .stream().map(WorkspaceNoticeRecipientEntity::getRecipientId).collect(java.util.stream.Collectors.toSet());
            if(!existing.getTitle().equals(input.title().trim()) || !existing.getContent().equals(input.content().trim()) || !previous.equals(ids))
                throw new CoolException("该请求标识已用于其他通知",409);
            return existing.getId();
        }
        var notice=new WorkspaceNoticeEntity();notice.setSenderId(id);notice.setRequestId(input.requestId());
        notice.setTitle(input.title().trim());notice.setContent(input.content().trim());notices.insert(notice);
        for(Long target:ids){var r=new WorkspaceNoticeRecipientEntity();r.setNoticeId(notice.getId());r.setRecipientId(target);recipients.insert(r);}
        return notice.getId();
    }
    private void bounds(int page,int size){if(page<1 || size<1 || size>100)throw new CoolException("分页参数无效");}
    private String name(Long id){var u=users.selectOneById(id);return u==null?"历史账号":Objects.toString(u.getName(),u.getUsername());}
    private NoticeView view(WorkspaceNoticeEntity n,Date readAt){return new NoticeView(n.getId(),n.getTitle(),n.getContent(),name(n.getSenderId()),n.getCreateTime(),readAt);}
    public PageResult<NoticeView> page(Long id,boolean sent,int page,int size){
        current(id);bounds(page,size);
        if(sent){
            var result=notices.paginate(page,size,QueryWrapper.create().eq(WorkspaceNoticeEntity::getSenderId,id).orderBy(WorkspaceNoticeEntity::getId,false));
            return new PageResult<>(result.getRecords().stream().map(n->view(n,null)).toList(),result.getTotalRow(),page,size);
        }
        var result=recipients.paginate(page,size,QueryWrapper.create().eq(WorkspaceNoticeRecipientEntity::getRecipientId,id).orderBy(WorkspaceNoticeRecipientEntity::getId,false));
        return new PageResult<>(result.getRecords().stream().map(r->view(notices.selectOneById(r.getNoticeId()),r.getReadAt())).toList(),result.getTotalRow(),page,size);
    }
    public NoticeView detail(Long id,Long noticeId){
        current(id);var r=recipients.selectOneByQuery(QueryWrapper.create().eq(WorkspaceNoticeRecipientEntity::getRecipientId,id).eq(WorkspaceNoticeRecipientEntity::getNoticeId,noticeId));
        if(r==null)throw new CoolException("通知不存在或无权访问",404);
        return view(notices.selectOneById(noticeId),r.getReadAt());
    }
    public List<Receipt> receipts(Long id,Long noticeId){
        current(id);var n=notices.selectOneById(noticeId);
        if(n==null || !id.equals(n.getSenderId()))throw new CoolException("通知不存在或无权访问",404);
        return recipients.selectListByQuery(QueryWrapper.create().eq(WorkspaceNoticeRecipientEntity::getNoticeId,noticeId))
            .stream().map(r->new Receipt(r.getRecipientId(),name(r.getRecipientId()),r.getReadAt())).toList();
    }
    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void read(Long id,Long noticeId){
        current(id);var r=recipients.selectOneByQuery(QueryWrapper.create().eq(WorkspaceNoticeRecipientEntity::getRecipientId,id)
            .eq(WorkspaceNoticeRecipientEntity::getNoticeId,noticeId).forUpdate());
        if(r==null)throw new CoolException("通知不存在或无权访问",404);
        if(r.getReadAt()==null){r.setReadAt(new Date());recipients.update(r);}
    }
    public long unread(Long id){current(id);return recipients.selectCountByQuery(QueryWrapper.create().eq(WorkspaceNoticeRecipientEntity::getRecipientId,id).isNull(WorkspaceNoticeRecipientEntity::getReadAt));}
}
