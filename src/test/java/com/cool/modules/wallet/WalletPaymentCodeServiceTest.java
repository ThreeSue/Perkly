package com.cool.modules.wallet;

import com.cool.core.exception.CoolException;
import com.cool.modules.base.entity.sys.BaseSysUserEntity;
import com.cool.modules.coupon.service.ConsumptionOrderService;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.entity.MerchantStoreEntity;
import com.cool.modules.merchant.mapper.MerchantInfoMapper;
import com.cool.modules.merchant.mapper.MerchantStoreMapper;
import com.cool.modules.promotion.service.StampVisitService;
import com.cool.modules.support.service.SupportAccess;
import com.cool.modules.user.entity.UserInfoEntity;
import com.cool.modules.user.mapper.UserInfoMapper;
import com.cool.modules.wallet.entity.PlatformWalletEntity;
import com.cool.modules.wallet.entity.WalletBillEntity;
import com.cool.modules.wallet.entity.WalletPaymentCodeEntity;
import com.cool.modules.wallet.mapper.PlatformWalletMapper;
import com.cool.modules.wallet.mapper.WalletBillMapper;
import com.cool.modules.wallet.mapper.WalletPaymentCodeMapper;
import com.cool.modules.wallet.mapper.WalletTransactionMapper;
import com.cool.modules.wallet.service.PlatformWalletService;
import com.cool.modules.wallet.service.WalletActivityService;
import com.cool.modules.wallet.service.WalletPaymentCodeService;
import com.cool.modules.workspace.service.WorkspaceAuthorization;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证付款码失效、一次性使用、重复收款和顾客回执
 * @Version: v1.1
 * @Date: 2026-11-16
 * @Time: 13:46:53
 */
class WalletPaymentCodeServiceTest {
    private static final String CODE = "QGJPAY1." + "a".repeat(32);
    private final WalletPaymentCodeMapper codes = mock(WalletPaymentCodeMapper.class);
    private final PlatformWalletService wallet = mock(PlatformWalletService.class);
    private final WalletPaymentCodeService service = new WalletPaymentCodeService(codes, wallet, mock(SupportAccess.class));

    private WalletPaymentCodeEntity token(boolean expired) {
        var row = new WalletPaymentCodeEntity();
        row.setCustomerId(7L);
        row.setExpiresAt(new Date(System.currentTimeMillis() + (expired ? -5000 : 60000)));
        when(codes.selectOneByQuery(any(QueryWrapper.class))).thenReturn(row);
        return row;
    }

    private WalletPaymentCodeService.Collection collect(Long billId) {
        return new WalletPaymentCodeService.Collection(billId, 2, 4800L, CODE);
    }

    @Test
    void expiresUnusedCodesBeforeCallingTheWallet() {
        token(true);
        assertEquals(409, assertThrows(CoolException.class, () -> service.collect(8L, collect(9L))).getCode());
        verifyNoInteractions(wallet);
    }

    @Test
    void retriesOnlyTheOriginalBillAndKeepsTheSameSettlementRequestId() {
        var row = token(false);
        var bill = new WalletBillEntity();
        bill.setId(9L);
        when(wallet.collectByPaymentCode(eq(8L), eq(7L), eq(9L), eq(2), eq(4800L), anyString())).thenReturn(bill);
        assertSame(bill, service.collect(8L, collect(9L)));
        assertEquals(9L, row.getBillId());
        assertSame(bill, service.collect(8L, collect(9L)));
        var requests = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(wallet, times(2)).collectByPaymentCode(eq(8L), eq(7L), eq(9L), eq(2), eq(4800L), requests.capture());
        assertEquals(requests.getAllValues().get(0), requests.getAllValues().get(1));
        assertEquals(409, assertThrows(CoolException.class, () -> service.collect(8L, collect(10L))).getCode());
        verifyNoMoreInteractions(wallet);
    }

    @Test
    void unsuccessfulCollectionDoesNotConsumeTheCode() {
        var row = token(false);
        when(wallet.collectByPaymentCode(eq(8L), eq(7L), eq(9L), eq(2), eq(4800L), anyString()))
            .thenThrow(new CoolException("储值余额不足"));
        assertThrows(CoolException.class, () -> service.collect(8L, collect(9L)));
        assertNull(row.getBillId());
        verify(codes, never()).update(row);
    }

    @Test
    void returnsThePaidReceiptEvenAfterTheCodeExpires() {
        var row = token(true);
        row.setBillId(9L);
        when(wallet.paymentCodeReceipt(7L, 9L))
            .thenReturn(new PlatformWalletService.PaymentCodeReceipt("测试门店", 4800L, 200L));
        var status = service.status(7L, CODE);
        assertEquals("PAID", status.status());
        assertEquals(4800L, status.paidAmount());
        assertEquals(200L, status.balance());
    }

    @Test
    void changedQuoteAndWrongCouponOwnerCannotDebitTheWallet() {
        var checkout = new Checkout();
        checkout.bill.setCustomerId(6L);
        assertEquals(403, assertThrows(CoolException.class,
            () -> checkout.service.collectByPaymentCode(8L, 7L, 9L, 2, 4800L, "scan_original")).getCode());

        checkout.bill.setCustomerId(7L);
        when(checkout.activity.quote(3L, 6800L))
            .thenReturn(new WalletActivityService.Quote(6800L, 4700L, 2100L, null, null, null));
        assertEquals(409, assertThrows(CoolException.class,
            () -> checkout.service.collectByPaymentCode(8L, 7L, 9L, 2, 4800L, "scan_original")).getCode());
        verify(checkout.wallets, never()).update(any(PlatformWalletEntity.class));
        verifyNoInteractions(checkout.consumption);
        assertEquals("PENDING", checkout.bill.getStatus());
    }

    @Test
    void zeroCouponCompletesAndRetriesWithoutCreatingAWalletDebit() {
        var checkout = new Checkout();
        checkout.bill.setCustomerId(7L);
        checkout.bill.setConsumptionOrderId(18L);
        checkout.bill.setRemainingAmount(0L);
        checkout.bill.setCouponDiscount(6800L);
        var linked = mock(ConsumptionOrderService.OrderView.class);
        when(linked.payableAmount()).thenReturn(0L);
        when(checkout.consumption.settlementPreview(9L, 18L)).thenReturn(linked);
        when(checkout.consumption.adminInfo(18L)).thenReturn(linked);
        var result = checkout.service.confirmZeroCoupon(8L, 9L, 2);
        assertEquals("PAID", result.getStatus());
        assertEquals("ZERO_COUPON", result.getPaymentChannel());
        assertEquals(0L, result.getPaidAmount());
        assertSame(result, checkout.service.confirmZeroCoupon(8L, 9L, 2));
        verify(checkout.consumption, times(1)).settleLinked(9L, 18L, "SIMULATED", 0L);
        verifyNoInteractions(checkout.wallets, checkout.transactions);
    }

    @Test
    void searchAndReceiptUseTheSameStoreAndEmployeeScope() {
        var checkout = new Checkout();
        when(checkout.auth.permissions(8L)).thenReturn(Set.of("bill:view"));
        when(checkout.auth.ownRecordsOnly(checkout.employee)).thenReturn(true);
        var queries = org.mockito.ArgumentCaptor.forClass(QueryWrapper.class);

        assertSame(checkout.bill, checkout.service.staffBillByNumber(8L, checkout.bill.getBillNo()));
        checkout.bill.setStatus("PAID");
        checkout.bill.setPaidAmount(6800L);
        assertEquals(6800L, checkout.service.collectionResult(8L, 9L).getPaidAmount());

        verify(checkout.auth, times(2)).require(8L, "bill:view");
        verify(checkout.bills, times(2)).selectOneByQuery(queries.capture());
        for (var query : queries.getAllValues()) {
            String sql = query.toSQL().toLowerCase(java.util.Locale.ROOT);
            assertTrue(sql.contains("store_id"));
            assertTrue(sql.contains("operator_id"));
        }
        assertTrue(queries.getAllValues().get(0).toSQL().contains(checkout.bill.getBillNo()));
        assertTrue(queries.getAllValues().get(1).toSQL().toLowerCase(java.util.Locale.ROOT).contains("for update"));
        verifyNoInteractions(checkout.wallets, checkout.transactions);
    }

    private static final class Checkout {
        final PlatformWalletService service = mock(PlatformWalletService.class, CALLS_REAL_METHODS);
        final WalletBillEntity bill = new WalletBillEntity();
        final WalletBillMapper bills = mock(WalletBillMapper.class);
        final WorkspaceAuthorization auth = mock(WorkspaceAuthorization.class);
        final BaseSysUserEntity employee = new BaseSysUserEntity();
        final PlatformWalletMapper wallets = mock(PlatformWalletMapper.class);
        final WalletTransactionMapper transactions = mock(WalletTransactionMapper.class);
        final WalletActivityService activity = mock(WalletActivityService.class);
        final ConsumptionOrderService consumption = mock(ConsumptionOrderService.class);

        Checkout() {
            when(auth.user(8L)).thenReturn(employee);
            when(auth.storeIds(employee)).thenReturn(List.of(3L));
            var customers = mock(UserInfoMapper.class);
            var customer = new UserInfoEntity();
            customer.setStatus(1);
            when(customers.selectOneByQuery(any(QueryWrapper.class))).thenReturn(customer);
            when(wallets.selectOneByQuery(any(QueryWrapper.class))).thenReturn(new PlatformWalletEntity());
            var stores = mock(MerchantStoreMapper.class);
            when(stores.selectOneById(3L)).thenReturn(new MerchantStoreEntity());
            var merchants = mock(MerchantInfoMapper.class);
            when(merchants.selectOneById(4L)).thenReturn(new MerchantInfoEntity());
            bill.setId(9L);
            bill.setBillNo("W" + "b".repeat(32));
            bill.setStoreId(3L);
            bill.setMerchantId(4L);
            bill.setVersion(2);
            bill.setAmount(6800L);
            bill.setStatus("PENDING");
            bill.setCreateTime(new Date());
            when(bills.selectOneByQuery(any(QueryWrapper.class))).thenReturn(bill);
            ReflectionTestUtils.setField(service, "auth", auth);
            ReflectionTestUtils.setField(service, "access", mock(SupportAccess.class));
            ReflectionTestUtils.setField(service, "customers", customers);
            ReflectionTestUtils.setField(service, "wallets", wallets);
            ReflectionTestUtils.setField(service, "transactions", transactions);
            ReflectionTestUtils.setField(service, "bills", bills);
            ReflectionTestUtils.setField(service, "stores", stores);
            ReflectionTestUtils.setField(service, "merchants", merchants);
            ReflectionTestUtils.setField(service, "activities", activity);
            ReflectionTestUtils.setField(service, "consumption", consumption);
            ReflectionTestUtils.setField(service, "stampVisits", mock(StampVisitService.class));
        }
    }
}
