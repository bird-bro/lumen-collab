package org.birdbro.lumen.core.service.support;

import java.util.Date;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.core.entity.ExamOrderStatus;
import org.springframework.stereotype.Component;

/**
 * Derived entry-status policy for pre-registration creates (kernel-pre-registration
 * tasks 2.1/2.2, design D1/D4): a stateless collaborator (service/support, no HTTP
 * surface) the CONSUMING create flow calls BEFORE persisting - {@code registerOrder}
 * keeps its "entry state is the caller's choice" contract untouched (D1). The
 * derivation is pure time math: NULL/now/past → REGISTERED (现场单), strictly-future
 * → BOOKED (预登记态). The now boundary is the DB-SERVER time the caller supplies
 * (design Risks row 2: never an app clock) - this component stays unit-testable
 * without a datasource.
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
     * Derives the create entry status from the booking time (design D1).
     *
     * @param bookingTime the pre-registration slot anchor; {@code null} = walk-in
     * @param dbNow       the DB-server wall clock ({@code SELECT now()::timestamp});
     *                    required non-null for a non-null {@code bookingTime}
     * @return REGISTERED for NULL/at/past bookings, BOOKED for strictly-future ones
     */
    public ExamOrderStatus deriveEntryStatus(Date bookingTime, Date dbNow) {
        if (bookingTime == null) {
            return ExamOrderStatus.REGISTERED;
        }
        // future = strictly after the DB-server now (design D4); at-now and past
        // derive REGISTERED - a booking cannot enter BOOKED in the past
        return bookingTime.after(dbNow) ? ExamOrderStatus.BOOKED : ExamOrderStatus.REGISTERED;
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
