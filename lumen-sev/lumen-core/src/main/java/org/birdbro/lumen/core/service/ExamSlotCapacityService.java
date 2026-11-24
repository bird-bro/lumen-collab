package org.birdbro.lumen.core.service;

import java.util.Date;

import org.birdbro.lumen.core.entity.ExamSlotConfig;
import org.birdbro.lumen.core.entity.SlotWindowBounds;

/**
 * Slot-capacity validation for pre-registration creates (kernel-pre-registration
 * tasks 2.3-2.5, design D2/D4). The kernel owns the VALIDATION; the occupancy COUNT
 * belongs to the caller (D2 - the kernel is a schema-free library): the consuming
 * create flow counts its own live BOOKED orders in the window and hands the number
 * in. All refusals ride the house A0400 pattern (design D5).
 *
 * @author birdbro
 */
public interface ExamSlotCapacityService {

    /**
     * Resolves the slot window a booking time floors onto (design D4): the
     * time-of-day floors to the granularity grid inside an open window; the count
     * predicate is {@code booking_time >= windowStart AND < windowEnd}. Refuses
     * A0400 when the time is outside every open window (不在开台时间窗内) or the
     * config fails closed (empty/unparseable windows, non-positive granularity).
     *
     * @param config      the modality's live slot config (required)
     * @param bookingTime the requested pre-registration time (required)
     * @return the resolved slot bounds for the caller's occupancy count
     */
    SlotWindowBounds resolveSlotWindow(ExamSlotConfig config, Date bookingTime);

    /**
     * Validates one pre-registration create against the modality's live config
     * (design D2/D7): locks the config row FOR UPDATE (the same-slot serialization
     * point - run INSIDE the caller's create transaction), refuses unconfigured
     * modalities (该检查类型未开通预登记), refuses non-future times (预登记时间不能
     * 早于当前时间), delegates the window rule to {@link #resolveSlotWindow}, and
     * refuses at/above capacity (该时段已满).
     *
     * @param modality       the dict-1024 modality token (required)
     * @param bookingTime    the requested pre-registration time (required, future)
     * @param occupancyCount the caller's live BOOKED count for the resolved window
     * @return the resolved slot bounds (the count predicate's window)
     */
    SlotWindowBounds validatePreregistration(String modality, Date bookingTime, int occupancyCount);
}
