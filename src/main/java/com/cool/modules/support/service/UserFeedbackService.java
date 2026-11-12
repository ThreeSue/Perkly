package com.cool.modules.support.service;
import com.cool.core.exception.CoolException;
import com.cool.modules.support.entity.UserFeedbackEntity;
import com.cool.modules.support.mapper.UserFeedbackMapper;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.user.entity.UserInfoEntity;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 受理顾客反馈并由平台单次回复且保留处理快照
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:37:39
 */
@Service @RequiredArgsConstructor
public class UserFeedbackService {
    @org.springframework.beans.factory.annotation.Autowired private com.cool.modules.searchrisk.SearchRiskService risk;
    private final UserFeedbackMapper feedback;
    private final SupportAccess access;
    private final SupportImageService images;
    private final UserInfoMapper users;
    public record Submit(String requestId,String content,List<String> images) {}
    public record Reply(Long id,String content) {}
    public record PageResult<T>(List<T> list,long total,int pageNumber,int pageSize) {}
    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long submit(Long customer,Submit p) {
        access.customer(customer);
        if(p==null || p.content()==null || p.content().isBlank() || p.content().length()>2000)throw new CoolException("反馈内容需为1至2000字");
        SupportAccess.request(p.requestId());var urls=images.validate("APP",customer,p.images());
        risk.checkContent(p.content(),"FEEDBACK","FEEDBACK",null);
        users.selectOneByQuery(QueryWrapper.create().eq(UserInfoEntity::getId,customer).forUpdate());
        var old=feedback.selectOneByQuery(QueryWrapper.create().eq(UserFeedbackEntity::getCustomerId,customer).eq(UserFeedbackEntity::getRequestId,p.requestId()));
        if(old!=null){
            if(!old.getContent().equals(p.content().trim()) || !Objects.equals(old.getImages(),urls))throw new CoolException("请求标识已用于其他反馈",409);
            return old.getId();
        }
        var row=new UserFeedbackEntity();row.setCustomerId(customer);row.setRequestId(p.requestId());row.setContent(p.content().trim());row.setImages(urls);row.setStatus("PENDING");feedback.insert(row);risk.bind("FEEDBACK",row.getId());return row.getId();
    }
    public UserFeedbackEntity detail(Long customer,Long id){
        if(customer==null)access.platform("feedback:manage");else access.customer(customer);
        var row=feedback.selectOneById(id);
        if(row==null || (customer!=null && !customer.equals(row.getCustomerId())))throw new CoolException("反馈不存在或无权访问",404);
        return decorate(row);
    }
    public PageResult<UserFeedbackEntity> page(Long customer,String status,int page,int size){
        SupportAccess.page(page,size);
        if(customer==null)access.platform("feedback:manage");else access.customer(customer);
        if(status!=null && !Set.of("PENDING","REPLIED").contains(status))throw new CoolException("反馈状态无效");
        var query=QueryWrapper.create();
        if(customer!=null)query.eq(UserFeedbackEntity::getCustomerId,customer);
        if(status!=null)query.eq(UserFeedbackEntity::getStatus,status);
        var result=feedback.paginate(page,size,query.orderBy(UserFeedbackEntity::getId,false));
        return new PageResult<>(result.getRecords().stream().map(this::decorate).toList(),result.getTotalRow(),page,size);
    }
    private UserFeedbackEntity decorate(UserFeedbackEntity row){var u=users.selectOneById(row.getCustomerId());row.setCustomerName(u==null?"历史用户":Objects.toString(u.getNickName(),"顾客"));return row;}
    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reply(Long actor,Reply p){
        access.platform("feedback:manage");
        if(p==null || p.id()==null || p.content()==null || p.content().isBlank() || p.content().length()>2000)throw new CoolException("回复需为1至2000字");
        var row=feedback.selectOneByQuery(QueryWrapper.create().eq(UserFeedbackEntity::getId,p.id()).forUpdate());
        if(row==null)throw new CoolException("反馈不存在",404);
        if("REPLIED".equals(row.getStatus())){
            if(Objects.equals(actor,row.getRepliedBy()) && row.getReply().equals(p.content().trim()))return;
            throw new CoolException("反馈已回复，不支持多轮或覆盖回复",409);
        }
        risk.checkContent(p.content(),"FEEDBACK","FEEDBACK_REPLY",p.id());
        row.setReply(p.content().trim());row.setRepliedBy(actor);row.setRepliedAt(new Date());row.setStatus("REPLIED");feedback.update(row);
    }
}
