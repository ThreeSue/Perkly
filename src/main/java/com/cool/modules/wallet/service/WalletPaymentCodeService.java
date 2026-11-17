package com.cool.modules.wallet.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.cool.core.exception.CoolException;
import com.cool.modules.coupon.service.CouponTradeRules;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.wallet.entity.WalletBillEntity;
import com.cool.modules.wallet.entity.WalletPaymentCodeEntity;
import com.cool.modules.wallet.mapper.WalletPaymentCodeMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 签发一次性模拟储值付款码并事务化处理门店扫码收款
 * @Version: v1.1
 * @Date: 2026-11-16
 * @Time: 13:33:52
 */
@Service
@RequiredArgsConstructor
public class WalletPaymentCodeService {
    private static final String PREFIX = "QGJPAY1.";
    private static final int VALID_SECONDS = 60;
    private final WalletPaymentCodeMapper codes;
    private final PlatformWalletService wallet;
    private final SupportAccess access;

    public record CodeInput(String code) {}
    public record IssuedCode(String code, String image, int validSeconds) {}
    public record Collection(Long id, Integer version, Long expectedAmount, String code) {}
    public record BillVersion(Long id, Integer version) {}
    public record CodeStatus(String status, String storeName, Long paidAmount, Long balance) {}

    public IssuedCode issue(Long customer) {
        access.customer(customer);
        var code = PREFIX + UUID.randomUUID().toString().replace("-", "");
        var image = CouponTradeRules.qrImage(code);
        var row = new WalletPaymentCodeEntity();
        row.setCustomerId(customer);
        row.setTokenHash(digest(code));
        row.setExpiresAt(new Date(System.currentTimeMillis() + VALID_SECONDS * 1000L));
        codes.insert(row);
        return new IssuedCode(code, image, VALID_SECONDS);
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CodeStatus status(Long customer, String code) {
        access.customer(customer);
        var row = ownedCode(customer, code);
        if (row.getBillId() != null) {
            var receipt = wallet.paymentCodeReceipt(customer, row.getBillId());
            return new CodeStatus("PAID", receipt.storeName(), receipt.paidAmount(), receipt.balance());
        }
        return new CodeStatus(row.getExpiresAt().after(new Date()) ? "PENDING" : "EXPIRED", null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long customer, String code) {
        access.customer(customer);
        var row = ownedCode(customer, code);
        if (row.getBillId() == null && row.getExpiresAt().after(new Date())) {
            row.setExpiresAt(new Date());
            codes.update(row);
        }
    }

    @Transactional(rollbackFor = Exception.class, isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public WalletBillEntity collect(Long actor, Collection input) {
        if (input == null || input.id() == null || input.version() == null)
            throw new CoolException("请重新打开账单再收款", 400);
        var hash = digest(input.code());
        var row = codes.selectOneByQuery(QueryWrapper.create()
            .eq(WalletPaymentCodeEntity::getTokenHash, hash).forUpdate());
        if (row == null) throw new CoolException("付款码无效，请顾客重新出示", 400);
        if (row.getBillId() != null && !Objects.equals(row.getBillId(), input.id()))
            throw new CoolException("该付款码已使用，请顾客重新出示", 409);
        if (row.getBillId() == null && !row.getExpiresAt().after(new Date()))
            throw new CoolException("付款码已失效，请顾客重新出示", 409);
        var bill = wallet.collectByPaymentCode(actor, row.getCustomerId(), input.id(), input.version(),
            input.expectedAmount(), "scan_" + hash.substring(0, 56));
        if (row.getBillId() == null) {
            row.setBillId(bill.getId());
            codes.update(row);
        }
        return bill;
    }

    private WalletPaymentCodeEntity ownedCode(Long customer, String code) {
        var query = QueryWrapper.create().eq(WalletPaymentCodeEntity::getTokenHash, digest(code))
            .eq(WalletPaymentCodeEntity::getCustomerId, customer).forUpdate();
        var row = codes.selectOneByQuery(query);
        if (row == null) throw new CoolException("付款码无效", 404);
        return row;
    }

    private String digest(String code) {
        if (code == null || !code.matches("QGJPAY1\\.[a-f0-9]{32}"))
            throw new CoolException("请扫描顾客钱包中的付款码", 400);
        return DigestUtil.sha256Hex(code);
    }
}
