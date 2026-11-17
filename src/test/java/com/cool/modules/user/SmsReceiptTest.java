package com.cool.modules.user;

import com.cool.modules.user.sms.*;
import com.cool.modules.user.mapper.SmsReceiptMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: 蘇世睿
 * @Class: 移動互聯2412
 * @SeatNo: 21
 * @Description: 验证短信回执去重、脱敏与落库失败重投边界
 * @Version: v1.1
 * @Date: 2026-11-17
 * @Time: 16:58:00
 */
class SmsReceiptTest {
    private static final String BODY = """
        {"biz_id":"test-biz", "phone_number":"13800000000", "success":true,
         "err_code":"DELIVERED", "send_time":"2026-11-17 15:00:00", "report_time":"2026-11-17 15:00:05"}
        """;

    @Test void masksPhoneAndRejectsIncompleteReports() throws Exception {
        var report = new ObjectMapper().readValue(BODY, SmsReceiptStore.Report.class);
        var row = SmsReceiptStore.normalize(report);
        assertEquals("138****0000", row.getPhoneMask());
        assertEquals("DELIVERED", row.getResult());
        assertThrows(IllegalArgumentException.class, () -> SmsReceiptStore.normalize(
            new SmsReceiptStore.Report("test", "13800000000", null, null, null, null)));
    }

    @Test void duplicateReceiptDoesNotInsertAnotherDelivery() throws Exception {
        var mapper = mock(SmsReceiptMapper.class);
        var store = new SmsReceiptStore(mapper, new ObjectMapper());
        var row = SmsReceiptStore.normalize(new ObjectMapper().readValue(BODY, SmsReceiptStore.Report.class));
        when(mapper.insert(any(SmsReceipt.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.selectOneByQuery(any())).thenReturn(row);
        store.accept(BODY);
        verify(mapper, never()).update(any(SmsReceipt.class));
    }

    @Test void storageFailureDoesNotAcknowledgeAndRetryCanRecover() throws Exception {
        var queue = mock(SmsReceiptQueue.class);
        var store = mock(SmsReceiptStore.class);
        when(queue.receive()).thenReturn(List.of(new SmsReceiptQueue.Message("handle", BODY)));
        doThrow(new IllegalStateException("DATABASE_UNAVAILABLE")).doNothing().when(store).accept(BODY);
        var worker = new SmsReceiptWorker(queue, store);
        worker.poll();
        verify(queue, never()).acknowledge(anyString());
        assertEquals("ERROR", worker.status().state());
        worker.poll();
        verify(queue, times(1)).receive();
        ReflectionTestUtils.setField(worker, "retryAt", Instant.EPOCH);
        worker.poll();
        verify(queue).acknowledge("handle");
        assertEquals("RUNNING", worker.status().state());
        assertEquals(0, worker.status().failures());
    }

    @Test void commitPrecedesAcknowledgement() throws Exception {
        var queue = mock(SmsReceiptQueue.class);
        var store = mock(SmsReceiptStore.class);
        when(queue.receive()).thenReturn(List.of(new SmsReceiptQueue.Message("handle", BODY)));
        new SmsReceiptWorker(queue, store).poll();
        var order = inOrder(store, queue);
        order.verify(store).accept(BODY);
        order.verify(queue).acknowledge("handle");
    }

    @Test void emptyQueueIsConnectedNotAnError() throws Exception {
        var queue = mock(SmsReceiptQueue.class);
        when(queue.receive()).thenReturn(List.of());
        var worker = new SmsReceiptWorker(queue, mock(SmsReceiptStore.class));
        worker.poll();
        assertEquals("RUNNING", worker.status().state());
        assertNotNull(worker.status().lastPoll());
        assertNull(worker.status().lastReceipt());
    }

    @Test void malformedBodyNeverAcknowledged() throws Exception {
        var queue = mock(SmsReceiptQueue.class);
        when(queue.receive()).thenReturn(List.of(new SmsReceiptQueue.Message("handle", "not json")));
        var worker = new SmsReceiptWorker(queue, new SmsReceiptStore(mock(SmsReceiptMapper.class), new ObjectMapper()));
        worker.poll();
        verify(queue, never()).acknowledge(anyString());
        assertEquals("ERROR", worker.status().state());
    }
}
