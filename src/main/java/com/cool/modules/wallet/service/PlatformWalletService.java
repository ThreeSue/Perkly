package com.cool.modules.wallet.service;

import com.cool.core.exception.CoolException;
import com.cool.modules.wallet.entity.*;
import com.cool.modules.wallet.mapper.*;
import com.cool.modules.support.service.*;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.base.mapper.sys.BaseSysUserMapper;
import com.cool.modules.merchant.mapper.*;
import com.cool.modules.merchant.entity.*;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.cool.modules.workspace.mapper.WorkspaceCouponStoreMapper;
import com.cool.modules.workspace.entity.WorkspaceCouponStoreEntity;
import com.cool.modules.coupon.mapper.*;
import com.cool.modules.coupon.entity.*;
import com.cool.modules.coupon.service.CouponTradeService;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.*;
import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 事务处理平台储值模拟充值直接消费与购券扣款并保存优惠快照
 * @Version: v1.1
 * @Date: 2026-11-02
 * @Time: 01:47:02
 */
@Service
@RequiredArgsConstructor
public class PlatformWalletService {
    private final com.cool.modules.promotion.service.StampVisitService stampVisits;
    private final PlatformWalletMapper wallets;
    private final WalletTransactionMapper transactions;
    private final WalletBillMapper bills;
    private final UserInfoMapper customers;
    private final BaseSysUserMapper users;
    private final MerchantInfoMapper merchants;
    private final MerchantStoreMapper stores;
    private final PurchaseOrderMapper orders;
    private final PurchaseOrderItemMapper items;
    private final CouponTemplateMapper coupons;
    private final WorkspaceCouponStoreMapper couponStores;
    private final CouponTradeService trade;
    private final WorkspaceAuthorization auth;
    private final SupportAccess access;
    private final WalletActivityService activities;
    private final com.cool.modules.coupon.service.ConsumptionOrderService consumption;
    private final CustomerCouponMapper customerCoupons;

    public record Recharge(String requestId,
                           @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = IntegerCentsDeserializer.class) Long amount,
                           String result) {
    }

    public record BillInput(String requestId, Long storeId,
                            @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = IntegerCentsDeserializer.class) Long amount,
                            String remark) {
    }

    public record Pay(String requestId, String billNo, Long purchaseOrderId, Long storeId,
                      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = IntegerCentsDeserializer.class) Long expectedAmount) {
        public Pay(String requestId, String billNo, Long purchaseOrderId, Long storeId) {
            this(requestId, billNo, purchaseOrderId, storeId, null);
        }
    }
    public record ApplyCoupon(Long id, Integer version, String code) {}
    public record ImportConsumption(Long orderId) {}

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WalletBillEntity importConsumption(Long actor, Long orderId) {
        staffStores(actor,"redeem:use");
        var order=consumption.info(actor,orderId);
        var old=bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getConsumptionOrderId,orderId));
        if(old!=null)return staffBillInfo(actor,old.getId());
        if(!"PENDING".equals(order.status()) || order.consumptionAmount()==null)
            throw new CoolException("仅待结算金额券账单可转入顾客付款",409);
        var b=createBill(actor,new BillInput("legacy_order_"+orderId,order.storeId(),order.consumptionAmount(),order.remark()));
        if(!"PENDING".equals(b.getStatus()) || b.getConsumptionOrderId()!=null)
            throw new CoolException("转入请求已用于其他账单，请查询原账单",409);
        consumption.linkSettlement(actor,orderId,b.getId());
        var coupon=customerCoupons.selectOneById(order.customerCouponId());
        if(coupon==null)throw new CoolException("持券归属异常",409);
        b.setConsumptionOrderId(orderId);
        b.setCustomerId(coupon.getCustomerId());
        b.setCouponDiscount(order.discountAmount());
        b.setRemainingAmount(order.payableAmount());
        b.setVersion(version(b)+1);
        bills.update(b);
        return decorate(b);
    }
    public record ConsumptionPay(String requestId, String billNo, String paymentMode,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = IntegerCentsDeserializer.class) Long expectedAmount,
        String result) {}
    public record ConsumptionQuote(String billNo, Integer version, Long originalAmount, Long couponDiscount,
        Long remainingAmount, String paymentMode, WalletActivityService.Quote payment) {}

    public record TransactionView(Long id, String requestId, String type, String status, String customerName,
                                  String merchantName, String storeName,
                                  Long originalAmount, Long amount, Long discountAmount, String activityTitle,
                                  BigDecimal discountRate, Long balanceAfter, Long purchaseOrderId, Long billId,
                                  Date createTime) {
    }

    private void money(Long amount) {
        if (amount == null || amount < 1 || amount > 100_000_000L)
            throw new CoolException("金额须为1至100000000整数分");
    }

    private PlatformWalletEntity lock(Long customer) {
        access.customer(customer);
        var user = customers.selectOneByQuery(QueryWrapper.create().eq(UserInfoEntity::getId, customer).forUpdate());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw new CoolException("用户已失效", 403);
        var wallet = wallets.selectOneByQuery(QueryWrapper.create().eq(PlatformWalletEntity::getCustomerId, customer).forUpdate());
        if (wallet == null) {
            wallet = new PlatformWalletEntity();
            wallet.setCustomerId(customer);
            wallet.setBalance(0L);
            wallets.insert(wallet);
        }
        return wallet;
    }

    public long balance(Long customer) {
        access.customer(customer);
        var w = wallets.selectOneByQuery(QueryWrapper.create().eq(PlatformWalletEntity::getCustomerId, customer));
        return w == null ? 0 : w.getBalance();
    }

    private WalletTransactionEntity previous(Long customer, String request) {
        SupportAccess.request(request);
        return transactions.selectOneByQuery(QueryWrapper.create().eq(WalletTransactionEntity::getCustomerId, customer).eq(WalletTransactionEntity::getRequestId, request).forUpdate());
    }

    public TransactionView view(WalletTransactionEntity r) {
        return view(r, true);
    }

    private TransactionView view(WalletTransactionEntity r, boolean exposeBalance) {
        if (r.getCreateTime() == null && r.getId() != null) r = transactions.selectOneById(r.getId());
        var c = customers.selectOneById(r.getCustomerId());
        return new TransactionView(r.getId(), r.getRequestId(), r.getType(), r.getStatus(), c == null ? "历史用户" : Objects.toString(c.getNickName(), "顾客"), r.getMerchantName(), r.getStoreName(), r.getOriginalAmount(), r.getAmount(), r.getDiscountAmount(), r.getActivityTitle(), r.getDiscountRate(), exposeBalance ? r.getBalanceAfter() : null, r.getPurchaseOrderId(), r.getBillId(), r.getCreateTime());
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TransactionView recharge(Long customer, Recharge p) {
        if (p == null) throw new CoolException("缺少充值参数");
        money(p.amount());
        SupportAccess.request(p.requestId());
        String status = p.result() == null ? "SUCCESS" : p.result();
        if (!Set.of("SUCCESS", "FAILED").contains(status)) throw new CoolException("模拟结果须为SUCCESS或FAILED");
        var w = lock(customer);
        var old = previous(customer, p.requestId());
        if (old != null) {
            if (!"RECHARGE".equals(old.getType()) || !old.getOriginalAmount().equals(p.amount()) || !old.getStatus().equals(status))
                throw new CoolException("请求标识已用于其他交易", 409);
            return view(old);
        }
        if ("SUCCESS".equals(status)) {
            try {
                w.setBalance(Math.addExact(w.getBalance(), p.amount()));
            } catch (ArithmeticException e) {
                throw new CoolException("余额超出上限");
            }
            if (w.getBalance() > 1_000_000_000L) throw new CoolException("储值余额超过本期演示上限");
            wallets.update(w);
        }
        var row = new WalletTransactionEntity();
        row.setCustomerId(customer);
        row.setRequestId(p.requestId());
        row.setType("RECHARGE");
        row.setStatus(status);
        row.setOriginalAmount(p.amount());
        row.setAmount("SUCCESS".equals(status) ? p.amount() : 0L);
        row.setDiscountAmount(0L);
        row.setDiscountRate(BigDecimal.TEN);
        row.setBalanceAfter(w.getBalance());
        transactions.insert(row);
        return view(row);
    }

    private List<Long> staffStores(Long actor, String permission) {
        var u = auth.user(actor);
        auth.context(actor);
        auth.require(actor, permission);
        return auth.storeIds(u);
    }

    public List<WalletActivityService.StoreOption> storeOptions(Long actor) {
        return staffStores(actor, "redeem:use").stream().map(id -> {
            try {
                var s = activities.normalStore(id);
                return new WalletActivityService.StoreOption(id, s.getName(), merchants.selectOneById(s.getMerchantId()).getName());
            } catch (CoolException unavailable) {
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    private WalletBillEntity decorate(WalletBillEntity b) {
        if (b.getCreateTime() == null && b.getId() != null) b = bills.selectOneById(b.getId());
        var m = merchants.selectOneById(b.getMerchantId());
        b.setMerchantName(m == null ? "历史商家" : m.getName());
        var s = stores.selectOneById(b.getStoreId());
        b.setStoreName(s == null ? "历史门店" : s.getName());
        if(b.getVersion()==null)b.setVersion(1);
        if(b.getCouponDiscount()==null)b.setCouponDiscount(0L);
        if(b.getRemainingAmount()==null)b.setRemainingAmount(b.getAmount());
        if("PAID".equals(b.getStatus()) && b.getPaidAmount()==null && b.getTransactionId()!=null){
            var payment=transactions.selectOneById(b.getTransactionId());
            if(payment!=null){b.setPaidAmount(payment.getAmount());b.setPaymentMode("WALLET");}
        }
        return b;
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WalletBillEntity createBill(Long actor, BillInput p) {
        if (p == null || p.storeId() == null) throw new CoolException("请选择门店");
        money(p.amount());
        SupportAccess.request(p.requestId());
        if (p.remark() != null && p.remark().length() > 200) throw new CoolException("备注最多200字");
        if (!staffStores(actor, "redeem:use").contains(p.storeId())) throw new CoolException("无权开具此门店账单", 403);
        var s = activities.normalStore(p.storeId());
        users.selectOneByQuery(QueryWrapper.create().eq(BaseSysUserEntity::getId, actor).forUpdate());
        var old = bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getOperatorId, actor).eq(WalletBillEntity::getRequestId, p.requestId()));
        if (old != null) {
            if (!old.getStoreId().equals(p.storeId()) || !old.getAmount().equals(p.amount()) || !Objects.equals(old.getRemark(), p.remark()))
                throw new CoolException("请求标识已用于其他账单", 409);
            return decorate(old);
        }
        var b = new WalletBillEntity();
        b.setBillNo("W" + UUID.randomUUID().toString().replace("-", ""));
        b.setOperatorId(actor);
        b.setRequestId(p.requestId());
        b.setMerchantId(s.getMerchantId());
        b.setStoreId(s.getId());
        b.setAmount(p.amount());
        b.setRemark(p.remark());
        b.setStatus("PENDING");
        b.setVersion(1);
        b.setCouponDiscount(0L);
        b.setRemainingAmount(p.amount());
        bills.insert(b);
        return decorate(b);
    }

    public WalletBillEntity bill(Long customer, String no) {
        access.customer(customer);
        if (no == null || !no.matches("W[a-f0-9]{32}")) throw new CoolException("账单编号无效");
        var b = bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getBillNo, no));
        if (b == null || b.getCustomerId() != null && !customer.equals(b.getCustomerId()))
            throw new CoolException("账单不存在或无权访问", 404);
        return decorate(b);
    }

    private int version(WalletBillEntity bill) { return bill.getVersion()==null ? 1 : bill.getVersion(); }

    private WalletBillEntity staffBill(Long actor, Long id) {
        var allowed=staffStores(actor,"redeem:use");
        var b=bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getId,id).forUpdate());
        if(b==null || !allowed.contains(b.getStoreId())
            || auth.ownRecordsOnly(auth.user(actor)) && !actor.equals(b.getOperatorId()))
            throw new CoolException("账单不存在或无权处理",403);
        return b;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WalletBillEntity applyCoupon(Long actor, ApplyCoupon input) {
        if(input==null || input.code()==null)throw new CoolException("请提供券码");
        var b=staffBill(actor,input.id());
        if(!"PENDING".equals(b.getStatus()))throw new CoolException("账单已处理",409);
        var coupon=customerCoupons.selectOneByQuery(QueryWrapper.create().eq(CustomerCouponEntity::getCode,input.code()));
        if(coupon==null)throw new CoolException("券不存在或不可使用",403);
        if(b.getConsumptionOrderId()!=null){
            var existing=consumption.settlementPreview(b.getId(),b.getConsumptionOrderId());
            if(!coupon.getId().equals(existing.customerCouponId()))throw new CoolException("已选择其他券，请取消后重新开单",409);
            return decorate(b);
        }
        if(!Objects.equals(version(b),input.version()))throw new CoolException("账单已变化，请刷新",409);
        var order=consumption.prepare(actor,new com.cool.modules.coupon.service.ConsumptionOrderService.PrepareInput(
            b.getStoreId(),input.code(),b.getAmount(),b.getRemark(),"wallet_bill_"+b.getId()));
        consumption.linkSettlement(actor,order.id(),b.getId());
        b.setConsumptionOrderId(order.id());
        b.setCustomerId(coupon.getCustomerId());
        b.setCouponDiscount(order.discountAmount());
        b.setRemainingAmount(order.payableAmount());
        b.setVersion(version(b)+1);
        bills.update(b);
        return decorate(b);
    }

    private long remaining(WalletBillEntity bill) {
        if(bill.getConsumptionOrderId()==null)return bill.getAmount();
        var order=consumption.settlementPreview(bill.getId(),bill.getConsumptionOrderId());
        if(!Objects.equals(order.payableAmount(),bill.getRemainingAmount()))throw new CoolException("用券金额变化，请重新开单",409);
        return order.payableAmount();
    }

    private void paymentMode(String mode) {
        if(!Set.of("WALLET","SIMULATED").contains(Objects.toString(mode,"")))
            throw new CoolException("请选择储值或普通模拟支付");
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ConsumptionQuote consumptionQuote(Long customer, ConsumptionPay input) {
        paymentMode(input.paymentMode());
        var b=bill(customer,input.billNo());
        if(!"PENDING".equals(b.getStatus()))throw new CoolException("账单已处理",409);
        activities.normalStore(b.getStoreId());
        long remainder=remaining(b);
        var quote="WALLET".equals(input.paymentMode()) ? activities.quote(b.getStoreId(),remainder)
            : new WalletActivityService.Quote(remainder,remainder,0L,null,null,BigDecimal.TEN);
        return new ConsumptionQuote(b.getBillNo(),version(b),b.getAmount(),b.getAmount()-remainder,remainder,input.paymentMode(),quote);
    }

    private void completeBill(WalletBillEntity b, Long customer, String requestId, String mode,
                              WalletActivityService.Quote quote) {
        if(b.getConsumptionOrderId()!=null)consumption.settleLinked(b.getId(),b.getConsumptionOrderId(),mode,quote.amount());
        b.setStatus("PAID");
        b.setCustomerId(customer);
        b.setPaymentMode(mode);
        b.setPaymentRequestId(requestId);
        b.setPaidAmount(quote.amount());
        b.setVersion(version(b)+1);
        var snapshot=cn.hutool.json.JSONUtil.createObj()
            .set("remainingAmount",quote.originalAmount()).set("paidAmount",quote.amount())
            .set("activityDiscount",quote.discountAmount()).set("activityId",quote.activityId())
            .set("activityTitle",quote.activityTitle()).set("discountRate",quote.discountRate())
            .set("activityVersion",quote.activityVersion());
        snapshot.set("originalConsumption",b.getAmount());
        snapshot.set("couponDiscount",b.getCouponDiscount()==null?0:b.getCouponDiscount());
        snapshot.set("storeName",stores.selectOneById(b.getStoreId()).getName());
        snapshot.set("merchantName",merchants.selectOneById(b.getMerchantId()).getName());
        if(b.getConsumptionOrderId()!=null) snapshot.set("coupon",
            consumption.adminInfo(b.getConsumptionOrderId()).couponSnapshot());
        b.setSettlementSnapshot(snapshot.toString());
        try {
            bills.update(b);
            stampVisits.direct(b.getId());
        } catch(org.springframework.dao.DuplicateKeyException duplicate) {
            throw new CoolException("请求标识已用于其他消费，请查询原交易",409);
        }
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WalletBillEntity consumptionPay(Long customer, ConsumptionPay input) {
        paymentMode(input.paymentMode());
        SupportAccess.request(input.requestId());
        if(input.result()!=null && !Set.of("SUCCESS","FAILED").contains(input.result()))throw new CoolException("模拟结果无效");
        // 所有付款入口统一先锁顾客，再锁账单，储值入口采用相同顺序。
        access.customer(customer);
        customers.selectOneByQuery(QueryWrapper.create().eq(UserInfoEntity::getId,customer).forUpdate());
        var found=bill(customer,input.billNo());
        var b=bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getId,found.getId()).forUpdate());
        if("PAID".equals(b.getStatus())){
            if(!customer.equals(b.getCustomerId()) || !Objects.equals(input.requestId(),b.getPaymentRequestId())
                || !Objects.equals(input.paymentMode(),b.getPaymentMode()) || !Objects.equals(input.expectedAmount(),b.getPaidAmount()))
                throw new CoolException("账单已支付，请查询原交易",409);
            return decorate(b);
        }
        var quote=consumptionQuote(customer,input).payment();
        if(!Objects.equals(input.expectedAmount(),quote.amount()))throw new CoolException("应付金额已变化，请重新确认",409);
        if("FAILED".equals(input.result()))return decorate(b);
        if("WALLET".equals(input.paymentMode())){
            pay(customer,new Pay(input.requestId(),input.billNo(),null,null,input.expectedAmount()));
        }else{
            if(previous(customer,input.requestId())!=null)throw new CoolException("请求标识已用于储值交易",409);
            completeBill(b,customer,input.requestId(),"SIMULATED",quote);
        }
        return decorate(bills.selectOneById(b.getId()));
    }

    public WalletBillEntity staffBillInfo(Long actor, Long id) {
        var allowed=staffStores(actor,"bill:view");
        var b=bills.selectOneById(id);
        if(b==null || !allowed.contains(b.getStoreId())
            || auth.ownRecordsOnly(auth.user(actor)) && !actor.equals(b.getOperatorId()))
            throw new CoolException("账单不存在或无权访问",403);
        return decorate(b);
    }

    public UserFeedbackService.PageResult<WalletBillEntity> customerBills(Long customer,int page,int size) {
        access.customer(customer);
        SupportAccess.page(page,size);
        var result=bills.paginate(page,size,QueryWrapper.create().eq(WalletBillEntity::getCustomerId,customer)
            .orderBy(WalletBillEntity::getId,false));
        return new UserFeedbackService.PageResult<>(result.getRecords().stream().map(this::decorate).toList(),result.getTotalRow(),page,size);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void cancelBill(Long actor, Long id) {
        var allowed = staffStores(actor, "redeem:use");
        var b = bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getId, id).forUpdate());
        if (b == null || !allowed.contains(b.getStoreId())) throw new CoolException("账单不存在或无权访问", 404);
        if (auth.ownRecordsOnly(auth.user(actor)) && !actor.equals(b.getOperatorId()))
            throw new CoolException("只能取消本人开具的账单", 403);
        if ("PAID".equals(b.getStatus())) throw new CoolException("已支付账单暂不支持退款或取消");
        if(b.getConsumptionOrderId()!=null)consumption.cancelLinked(actor,b.getId(),b.getConsumptionOrderId());
        b.setStatus("CANCELLED");
        b.setVersion(version(b)+1);
        bills.update(b);
    }

    private long purchase(Long customer, Long orderId, Long storeId, boolean lock) {
        if (orderId == null || storeId == null) throw new CoolException("购券支付须提供订单和适用门店");
        var q = QueryWrapper.create().eq(PurchaseOrderEntity::getId, orderId).eq(PurchaseOrderEntity::getCustomerId, customer);
        if (lock) q.forUpdate();
        var o = orders.selectOneByQuery(q);
        if (o == null || !"PENDING".equals(o.getOrderStatus())) throw new CoolException("购券订单不存在或已完成", 409);
        if (o.getExpiresAt() == null || !o.getExpiresAt().after(new Date())) throw new CoolException("购券订单已过期");
        var s = activities.normalStore(storeId);
        if (!o.getMerchantId().equals(s.getMerchantId())) throw new CoolException("支付门店不属于订单商家", 403);
        var detail = items.selectListByQuery(QueryWrapper.create().eq(PurchaseOrderItemEntity::getOrderId, orderId));
        if (detail.size() != 1) throw new CoolException("订单明细异常");
        var coupon = coupons.selectOneById(detail.get(0).getCouponTemplateId());
        if (coupon == null || Integer.valueOf(1).equals(coupon.getScopeType()) && couponStores.selectCountByQuery(QueryWrapper.create().eq(WorkspaceCouponStoreEntity::getCouponTemplateId, coupon.getId()).eq(WorkspaceCouponStoreEntity::getStoreId, storeId)) == 0)
            throw new CoolException("优惠券不适用于此门店");
        long amount = o.getTotalAmount().movePointRight(2).longValueExact();
        money(amount);
        return amount;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WalletActivityService.Quote quote(Long customer, Pay p) {
        access.customer(customer);
        if (p == null) throw new CoolException("支付参数缺失");
        if (p.purchaseOrderId() != null) {
            if (p.billNo() != null) throw new CoolException("不能同时支付两类账单");
            return activities.quote(p.storeId(), purchase(customer, p.purchaseOrderId(), p.storeId(), false));
        }
        var b = bill(customer, p.billNo());
        if (!"PENDING".equals(b.getStatus())) throw new CoolException("账单已处理");
        return activities.quote(b.getStoreId(), remaining(b));
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public TransactionView pay(Long customer, Pay p) {
        if (p == null) throw new CoolException("支付参数缺失");
        SupportAccess.request(p.requestId());
        if ((p.billNo() == null) == (p.purchaseOrderId() == null)) throw new CoolException("请选择一种支付业务");
        var w = lock(customer);
        var old = previous(customer, p.requestId());
        if (old != null) {
            boolean same = Objects.equals(old.getPurchaseOrderId(), p.purchaseOrderId()) && (p.purchaseOrderId() == null || Objects.equals(old.getStoreId(), p.storeId()));
            if (old.getBillId() != null) {
                var b = bills.selectOneById(old.getBillId());
                same = same && b != null && Objects.equals(b.getBillNo(), p.billNo());
            }
            if ("RECHARGE".equals(old.getType()) || !same || !Objects.equals(old.getAmount(), p.expectedAmount()))
                throw new CoolException("请求标识已用于其他交易", 409);
            return view(old);
        }
        WalletBillEntity b = null;
        Long storeId = p.storeId();
        long original;
        if (p.purchaseOrderId() != null) original = purchase(customer, p.purchaseOrderId(), storeId, true);
        else {
            var found = bill(customer, p.billNo());
            b = bills.selectOneByQuery(QueryWrapper.create().eq(WalletBillEntity::getId, found.getId()).forUpdate());
            if (!"PENDING".equals(b.getStatus())) throw new CoolException("账单已支付或取消", 409);
            storeId = b.getStoreId();
            original = remaining(b);
        }
        var s = activities.normalStore(storeId);
        var quote = activities.quote(storeId, original);
        if (!Objects.equals(quote.amount(), p.expectedAmount()))
            throw new CoolException("应付金额已变化或未确认，请重新试算确认", 409);
        if (w.getBalance() < quote.amount()) throw new CoolException("储值余额不足");
        // 复用既有库存、限购、规则版本及发券事务，不再实现一套发券流程。
        if (p.purchaseOrderId() != null) trade.simulatePay(customer, p.purchaseOrderId(), "SUCCESS");
        w.setBalance(w.getBalance() - quote.amount());
        wallets.update(w);
        var row = new WalletTransactionEntity();
        row.setCustomerId(customer);
        row.setRequestId(p.requestId());
        row.setType(b == null ? "PURCHASE" : "DIRECT");
        row.setStatus("SUCCESS");
        row.setPurchaseOrderId(p.purchaseOrderId());
        row.setBillId(b == null ? null : b.getId());
        row.setMerchantId(s.getMerchantId());
        row.setStoreId(storeId);
        row.setStoreName(s.getName());
        row.setMerchantName(merchants.selectOneById(s.getMerchantId()).getName());
        row.setOriginalAmount(original);
        row.setAmount(quote.amount());
        row.setDiscountAmount(quote.discountAmount());
        row.setActivityId(quote.activityId());
        row.setActivityTitle(quote.activityTitle());
        row.setDiscountRate(quote.discountRate());
        row.setBalanceAfter(w.getBalance());
        transactions.insert(row);
        if (b != null) {
            b.setTransactionId(row.getId());
            completeBill(b,customer,p.requestId(),"WALLET",quote);
        }
        return view(row);
    }

    public UserFeedbackService.PageResult<TransactionView> transactions(Long actor, String kind, int page, int size) {
        SupportAccess.page(page, size);
        var q = QueryWrapper.create();
        if ("APP".equals(kind)) {
            access.customer(actor);
            q.eq(WalletTransactionEntity::getCustomerId, actor);
        } else if ("PLATFORM".equals(kind)) access.platform("wallet:manage");
        else {
            var allowed = staffStores(actor, "bill:view");
            if (allowed.isEmpty()) q.eq(WalletTransactionEntity::getId, -1L);
            else q.in(WalletTransactionEntity::getStoreId, allowed);
            if (auth.ownRecordsOnly(auth.user(actor))) {
                var own = bills.selectListByQuery(QueryWrapper.create().eq(WalletBillEntity::getOperatorId, actor)).stream().map(WalletBillEntity::getId).toList();
                if (own.isEmpty()) q.eq(WalletTransactionEntity::getId, -1L);
                else q.in(WalletTransactionEntity::getBillId, own);
            }
        }
        var result = transactions.paginate(page, size, q.orderBy(WalletTransactionEntity::getId, false));
        return new UserFeedbackService.PageResult<>(result.getRecords().stream().map(row -> view(row, !"WORKSPACE".equals(kind))).toList(), result.getTotalRow(), page, size);
    }

    public record AccountView(String customerName, Long balance, Date updateTime) {
    }

    public UserFeedbackService.PageResult<AccountView> accounts(int page, int size) {
        access.platform("wallet:manage");
        SupportAccess.page(page, size);
        var result = wallets.paginate(page, size, QueryWrapper.create().orderBy(PlatformWalletEntity::getId, false));
        return new UserFeedbackService.PageResult<>(result.getRecords().stream().map(w -> {
            var c = customers.selectOneById(w.getCustomerId());
            return new AccountView(c == null ? "历史用户" : Objects.toString(c.getNickName(), "顾客"), w.getBalance(), w.getUpdateTime());
        }).toList(), result.getTotalRow(), page, size);
    }

    public UserFeedbackService.PageResult<WalletBillEntity> bills(Long actor, int page, int size) {
        SupportAccess.page(page, size);
        var allowed = staffStores(actor, "bill:view");
        var q = QueryWrapper.create();
        if (allowed.isEmpty()) q.eq(WalletBillEntity::getId, -1L);
        else q.in(WalletBillEntity::getStoreId, allowed);
        if (auth.ownRecordsOnly(auth.user(actor))) q.eq(WalletBillEntity::getOperatorId, actor);
        var result = bills.paginate(page, size, q.orderBy(WalletBillEntity::getId, false));
        return new UserFeedbackService.PageResult<>(result.getRecords().stream().map(this::decorate).toList(), result.getTotalRow(), page, size);
    }
}
