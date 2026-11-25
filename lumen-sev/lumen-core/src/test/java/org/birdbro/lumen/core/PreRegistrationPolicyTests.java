package org.birdbro.lumen.core;

import java.util.Date;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.birdbro.lumen.core.service.support.PreRegistrationPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Derived entry-status policy (kernel-pre-registration tasks 2.1/2.2; spec
 * exam-workflow-kernel "Pre-Registration Support" - Entry status derives from
 * booking time): NULL/now/past → REGISTERED (现场单 submit registers directly),
 * future → BOOKED (预登记态). PURE time math - unit-tested without DB; the now
 * boundary is the DB-server time the caller supplies (design D4).
 *
 * @author birdbro
 */
class PreRegistrationPolicyTests {

    private final PreRegistrationPolicy policy = new PreRegistrationPolicy();

    /** A fixed wall-clock "now" (the DB-server-time stand-in). */
    private final Date now = new Date(1732278000000L); // 2026-11-23T11:00:00 local wall clock

    @Test
    void nullBookingTimeDerivesRegistered() {
        assertEquals(ExamOrderStatus.REGISTERED, policy.deriveEntryStatus(null, now),
                "walk-in orders (NULL booking_time) register directly");
    }

    @Test
    void bookingAtNowDerivesRegistered() {
        assertEquals(ExamOrderStatus.REGISTERED, policy.deriveEntryStatus(now, now),
                "booking exactly at now is NOT a future slot - REGISTERED");
    }

    @Test
    void bookingInThePastDerivesRegistered() {
        Date past = new Date(now.getTime() - 60_000L);
        assertEquals(ExamOrderStatus.REGISTERED, policy.deriveEntryStatus(past, now),
                "a past booking_time cannot enter BOOKED");
    }

    @Test
    void futureBookingTimeDerivesRegistered() {
        // us-prereg-as-registered (预约=预登记): a strictly-future slot enters 已登记
        // too - the slot anchor records the appointment, the category rides
        // registrationType (not the status column)
        Date future = new Date(now.getTime() + 60_000L);
        assertEquals(ExamOrderStatus.REGISTERED, policy.deriveEntryStatus(future, now),
                "every create path enters REGISTERED (预登记=已登记)");
    }

    // ---------- the pre-registration-path future guard (design D4) ----------

    @Test
    void requireFutureBookingRefusesPast() {
        Date past = new Date(now.getTime() - 60_000L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> policy.requireFutureBooking(past, now),
                "a past pre-registration attempt refuses before any count/resolve dance");
        assertEquals(ExceptionEnum.ERROR_A0400.getCode(), ex.getCode(),
                "the house business-refusal code");
        assertTrue(ex.getMsg().contains("预登记时间不能早于当前时间"), "the D5 past token: " + ex.getMsg());
    }

    @Test
    void requireFutureBookingRefusesAtNow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> policy.requireFutureBooking(now, now),
                "at-now is not a future slot - the derivation routes it to REGISTERED");
        assertTrue(ex.getMsg().contains("预登记时间不能早于当前时间"), "the D5 past token: " + ex.getMsg());
    }

    @Test
    void requireFutureBookingRefusesNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> policy.requireFutureBooking(null, now),
                "a null booking on the PRE-REGISTRATION path is a caller bug - refuse loudly");
        assertTrue(ex.getMsg().contains("预登记时间不能早于当前时间"), "the D5 past token: " + ex.getMsg());
    }

    @Test
    void requireFutureBookingAcceptsFuture() {
        Date future = new Date(now.getTime() + 60_000L);
        assertDoesNotThrow(() -> policy.requireFutureBooking(future, now),
                "a strictly-future booking passes the guard");
    }
}
