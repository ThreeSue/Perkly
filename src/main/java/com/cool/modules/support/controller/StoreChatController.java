package com.cool.modules.support.controller;
import com.cool.core.request.R;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.controller.app.AppOrderController;
import com.cool.modules.support.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供顾客与门店工作人员共用的隔离会话接口
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:43:33
 */
@RestController @RequiredArgsConstructor @RequestMapping({"/app/support/chats","/merchant/governance/chats"})
public class StoreChatController {
    private final StoreChatService chats;
    private final SupportImageService images;
    private final HttpServletRequest request;
    public record Open(Long storeId){}
    private String kind(){return request.getRequestURI().startsWith("/app/")?"APP":"WORKSPACE";}
    private Long actor(){
        if("APP".equals(kind()))return AppOrderController.customer();
        if(request.getAttribute("merchantUserId") instanceof Long id)return id;
        throw new CoolException("请先登录",401);
    }
    @PostMapping("/open") public R<?> open(@RequestBody Open p){if(!"APP".equals(kind()))throw new CoolException("仅顾客可发起会话",403);return R.ok(chats.open(actor(),p.storeId()));}
    @GetMapping("/page") public R<?> page(@RequestParam(defaultValue="1") int pageNumber,@RequestParam(defaultValue="20") int pageSize){return R.ok(chats.page(kind(),actor(),pageNumber,pageSize));}
    @GetMapping("/messages") public R<?> messages(@RequestParam Long chatId,@RequestParam(required=false) Long afterId,@RequestParam(required=false) Long beforeId,@RequestParam(defaultValue="50") int pageSize){return R.ok(chats.messages(kind(),actor(),chatId,afterId,beforeId,pageSize));}
    @PostMapping("/send") public R<?> send(@RequestBody StoreChatService.Send p){return R.ok(chats.send(kind(),actor(),p));}
    @PostMapping(value="/images/upload",consumes="multipart/form-data") public R<?> upload(@RequestPart("file") MultipartFile file){
        Long id=actor();if(!"WORKSPACE".equals(kind()))throw new CoolException("顾客请使用通用客服图片上传",403);
        chats.requireStaff(id);return R.ok(images.upload(kind(),id,file,request));
    }
}
