package com.cool.modules.searchrisk;
import com.cool.modules.searchrisk.mapper.*;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.workspace.service.WorkspaceNoticeService;
import com.cool.modules.workspace.mapper.*;
import com.cool.modules.workspace.entity.*;
import com.cool.core.exception.CoolException;
import com.cool.core.util.CoolSecurityUtil;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 统一内容风险检测与通知任务
 * @Version: v1.0
 * @Date: 2026-11-12
 * @Time: 14:00:00
 */

@org.springframework.stereotype.Service @lombok.RequiredArgsConstructor
public class PlatformWarningService {
 private final SearchRiskMapper risks; private final PlatformWarningMapper warnings; private final SearchRiskAuditMapper audits;
 private final SupportAccess access; private final SearchRiskService detection; private final UserInfoMapper users;
 private final WorkspaceNoticeService workspace; private final WorkspaceNoticeMapper notices; private final WorkspaceNoticeRecipientMapper recipients;
 public record Send(Long riskId,String requestId,String title,String content,String noticeType){}
 public record Preview(Long riskId,String subjectType,Long subjectId,String title,String content,String noticeType){}
 public record View(Long id,Long riskId,String title,String content,String noticeType,Date createTime,Date readAt,Date confirmedAt,Long workspaceNoticeId){}
 private SearchRisk target(Long id,boolean lock){
  if(id==null)throw new CoolException("缺少风险记录",400);
  var q=QueryWrapper.create().eq(SearchRisk::getId,id);if(lock)q.forUpdate();var r=risks.selectOneByQuery(q);
  if(r==null)throw new CoolException("风险记录不存在",404);
  if(Set.of("IGNORED","FALSE_POSITIVE").contains(r.getStatus()))throw new CoolException("已忽略的记录不能发送警告",409);
  if(r.getSubjectId()==null||!Set.of("APP","MERCHANT").contains(Objects.toString(r.getSubjectType(),"")))throw new CoolException("该记录没有可接收站内通知的真实用户或商家账号",409);
  if("APP".equals(r.getSubjectType())&&users.selectOneById(r.getSubjectId())==null)throw new CoolException("接收用户不存在",404);
  return r;
 }
 public Preview preview(Long riskId){access.platform("search:risk:manage");var r=target(riskId,false);return new Preview(r.getId(),r.getSubjectType(),r.getSubjectId(),"平台内容提醒","您好，您提交的部分内容需要关注。请核对平台使用规则并及时调整。如有疑问，可联系平台反馈。本提醒不代表账号已被封禁；确认仅表示已知悉。","LIGHT");}
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public View send(Send p){
  access.platform("search:risk:manage");access.platform("notice:send");
  SupportAccess.request(p.requestId());
  if(p.title()==null||p.title().isBlank()||p.title().length()>100||p.content()==null||p.content().isBlank()||p.content().length()>5000||!Set.of("LIGHT","STRONG").contains(Objects.toString(p.noticeType(),"")))throw new CoolException("警告文案或类型无效",400);
  Long actor=CoolSecurityUtil.getCurrentUserId();var r=target(p.riskId(),true);
  var old=warnings.selectOneByQuery(QueryWrapper.create().eq(PlatformWarning::getSenderId,actor).eq(PlatformWarning::getRequestId,p.requestId()));
  if(old!=null){if(!Objects.equals(old.getRiskId(),p.riskId())||!old.getTitle().equals(p.title())||!old.getContent().equals(p.content())||!old.getNoticeType().equals(p.noticeType()))throw new CoolException("请求标识已用于其他警告",409);return view(old);}
  detection.checkContent(p.title(),"NOTICE","RISK_WARNING_TITLE",r.getId());detection.checkContent(p.content(),"NOTICE","RISK_WARNING_CONTENT",r.getId());
  var n=new PlatformWarning();n.setRiskId(r.getId());n.setSenderId(actor);n.setRequestId(p.requestId());n.setSubjectType(r.getSubjectType());n.setSubjectId(r.getSubjectId());n.setTitle(p.title());n.setContent(p.content());n.setNoticeType(p.noticeType());
  if("MERCHANT".equals(r.getSubjectType())){
   Long noticeId=workspace.send(actor,new WorkspaceNoticeService.SendInput("risk_"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(p.requestId()).substring(0,48),p.title(),p.content(),List.of(r.getSubjectId()),p.noticeType()));
   n.setWorkspaceNoticeId(noticeId);var notice=notices.selectOneById(noticeId);notice.setBusinessType("CONTENT_RISK");notice.setBusinessId(r.getId());notices.update(notice);
  }
  try{warnings.insert(n);}catch(org.springframework.dao.DuplicateKeyException duplicate){throw new CoolException("请求标识已用于其他警告，请刷新核对",409);}audit(r.getId(),"WARNING_SEND",n.getId());return view(n);
 }
 private void audit(Long id,String action,Object value){var a=new SearchRiskAudit();a.setTargetId(id);a.setOperatorId(CoolSecurityUtil.getCurrentUserId());a.setAction(action);a.setDetail(cn.hutool.json.JSONUtil.toJsonStr(value));audits.insert(a);}
 public View view(PlatformWarning n){
  Date read=n.getReadAt(),confirmed=n.getConfirmedAt();
  if(n.getWorkspaceNoticeId()!=null){var r=recipients.selectOneByQuery(QueryWrapper.create().eq(WorkspaceNoticeRecipientEntity::getNoticeId,n.getWorkspaceNoticeId()).eq(WorkspaceNoticeRecipientEntity::getRecipientId,n.getSubjectId()));if(r!=null){read=r.getReadAt();confirmed=r.getConfirmedAt();}}
  return new View(n.getId(),n.getRiskId(),n.getTitle(),n.getContent(),n.getNoticeType(),n.getCreateTime(),read,confirmed,n.getWorkspaceNoticeId());
 }
 public List<View> receipts(Long riskId){access.platform("search:risk:view");return warnings.selectListByQuery(QueryWrapper.create().eq(PlatformWarning::getRiskId,riskId).orderBy(PlatformWarning::getId,false).limit(100)).stream().map(this::view).toList();}
 private Long customer(){if(CoolSecurityUtil.getCurrentUserType()!=com.cool.core.enums.UserTypeEnum.APP)throw new CoolException("请使用用户身份",403);Long id=CoolSecurityUtil.getCurrentUserId();access.customer(id);return id;}
 private QueryWrapper own(Long id){return QueryWrapper.create().eq(PlatformWarning::getSubjectType,"APP").eq(PlatformWarning::getSubjectId,id);}
 public Object page(int page,int size,boolean pending){Long id=customer();SupportAccess.page(page,size);var q=own(id);if(pending)q.eq(PlatformWarning::getNoticeType,"STRONG").isNull(PlatformWarning::getConfirmedAt);var p=warnings.paginate(page,size,q.orderBy(PlatformWarning::getId,false));return new com.cool.modules.support.service.UserFeedbackService.PageResult<>(p.getRecords().stream().map(this::view).toList(),p.getTotalRow(),page,size);}
 public long unread(){return warnings.selectCountByQuery(own(customer()).isNull(PlatformWarning::getReadAt));}
 private PlatformWarning ownNotice(Long id,boolean lock){if(id==null)throw new CoolException("缺少通知编号",400);var q=own(customer()).eq(PlatformWarning::getId,id);if(lock)q.forUpdate();var n=warnings.selectOneByQuery(q);if(n==null)throw new CoolException("通知不存在或无权访问",404);return n;}
 public View detail(Long id){return view(ownNotice(id,false));}
 @org.springframework.transaction.annotation.Transactional public View read(Long id,boolean confirm){
  var n=ownNotice(id,true);if(confirm&&!"STRONG".equals(n.getNoticeType()))throw new CoolException("轻通知无需确认",400);
  if(n.getReadAt()==null)n.setReadAt(new Date());if(confirm&&n.getConfirmedAt()==null)n.setConfirmedAt(new Date());warnings.update(n);return view(n);
 }
}

