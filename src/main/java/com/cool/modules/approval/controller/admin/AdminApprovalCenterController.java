package com.cool.modules.approval.controller.admin;
import com.cool.core.annotation.CoolRestController;
import com.cool.core.enums.UserTypeEnum;
import com.cool.core.exception.CoolException;
import com.cool.core.request.R;
import com.cool.core.util.CoolSecurityUtil;
import com.cool.modules.approval.service.ApprovalCenterService;
import lombok.RequiredArgsConstructor;
import com.cool.modules.approval.entity.req.ApprovalReviewReq;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 提供运营审批中心的分页详情及受权限保护的审核接口
 * @Version: v1.1
 * @Date: 2026-10-21
 * @Time: 11:12:00
 */
@CoolRestController(api={"page","info","review"})
@RequiredArgsConstructor
public class AdminApprovalCenterController {
    private final ApprovalCenterService approvals;
    @GetMapping("/page")
    public R<?> page(@RequestParam String type,@RequestParam(required=false) Integer status,@RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        require(false); return R.ok(approvals.page(type,status,keyword,page,size));
    }
    @GetMapping("/info")
    public R<?> info(@RequestParam String type,@RequestParam Long id) {
        require(false); var result=approvals.info(type,id); result.setCanReview(canReview()); return R.ok(result);
    }
    @PostMapping("/review")
    public R<?> review(@RequestBody ApprovalReviewReq input) {
        require(true); approvals.review(input.getType(),input.getId(),input.getVersion(),input.getApproved(),input.getReason()); return R.ok();
    }
    private boolean canReview() {
        return CoolSecurityUtil.isSuperAdmin() || SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(p->"approval:center:review".equals(p.getAuthority()));
    }
    private void require(boolean review) {
        if (CoolSecurityUtil.getCurrentUserType()!=UserTypeEnum.ADMIN) throw new CoolException("请使用运营账号",403);
        if (review ? !canReview() : !canReview() && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().noneMatch(p->"approval:center:page".equals(p.getAuthority()))) throw new CoolException("没有审批中心权限",403);
    }
}
