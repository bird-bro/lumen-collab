package org.birdbro.lumen.core.service.support;

import java.util.Date;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.springframework.stereotype.Component;

/**
 * Derived entry-status policy for pre-registration creates (kernel-pre-registration
 * tasks 2.1/2.2, design D1/D4; re-anchored by us-prereg-as-registered 2026-11-24,
 * user ruling 预约=预登记): a stateless collaborator (service/support, no HTTP
 * surface) the CONSUMING create flow calls BEFORE persisting - {@code registerOrder}
 * keeps its "entry state is the caller's choice" contract untouched (D1). The
 * derivation is CONSTANT REGISTERED now - every create path enters 已登记 (a future
 * slot marks a 预登记 order, not a pending state; the category rides
 * {@code ExamOrder.registrationType}, stamped by the department branch). The
 * past-refusal guard below still enforces the strictly-future slot anchor. The now
 * boundary is the DB-SERVER time the caller supplies (design Risks row 2: never an
 * app clock) - this component stays unit-testable without a datasource.
 *
 * <p>This class owns the D5 past-refusal token (预登记时间不能早于当前时间): the
 * composed pre-registration flow calls {@link #requireFutureBooking} BEFORE the
 * resolve/count dance (D4 - a past attempt is refused before any count runs), and
 * the capacity service's validate delegates its future-only guard here so the
 * wording has exactly one home.</p>
 *
 * @author birdbro
 */
@Component
public class PreRegistrationPolicy {

    /** The D5 message token - refusal wording is contract, tests pin it. */
    static final String MSG_PAST = "预登记时间不能早于当前时间";

    /**
     * Derives the create entry status (design D1; re-anchored 2026-11-24): REGISTERED
     * for EVERY path - a NULL booking is a 现场单, a strictly-future slot is a 预登记
     * and both are registered (预约=预登记; the category marker rides
     * {@code registrationType}, not the status column).
     *
     * @param bookingTime the pre-registration slot anchor; {@code null} = walk-in
     * @param dbNow       the DB-server wall clock ({@code SELECT now()::timestamp});
     *                    kept for the signature's stability (the past guard uses it)
     * @return REGISTERED always
     */
    public ExamOrderStatus deriveEntryStatus(Date bookingTime, Date dbNow) {
        // 预登记=已登记 (user ruling 2026-11-24): the booking_time slot anchor records
        // the appointment; the status column starts every order at 已登记
        return ExamOrderStatus.REGISTERED;
    }

    /**
     * Guards a PRE-REGISTRATION attempt (design D4): a non-future booking time
     * refuses A0400 - the user explicitly picked a slot that cannot exist. The
     * walk-in path never calls this (a {@code null} booking derives REGISTERED
     * via {@link #deriveEntryStatus}); on the pre-registration path a {@code null}
     * time is a caller bug and refuses loudly like a past time.
     *
     * @param bookingTime the requested pre-registration time (required)
     * @param dbNow       the DB-server wall clock (required)
     * @throws BusinessException A0400 预登记时间不能早于当前时间 when the time is
     *                           not strictly future
     */
    public void requireFutureBooking(Date bookingTime, Date dbNow) {
        if (bookingTime == null || !bookingTime.after(dbNow)) {
            throw new BusinessException(ExceptionEnum.ERROR_A0400, MSG_PAST);
        }
    }
}
