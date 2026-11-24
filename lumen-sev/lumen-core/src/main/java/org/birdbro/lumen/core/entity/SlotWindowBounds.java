package org.birdbro.lumen.core.entity;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The resolved slot window handed back by the kernel's pre-registration validation
 * (kernel-pre-registration tasks 2.4/2.5, design D2/D4): the caller's occupancy
 * count runs {@code booking_time >= windowStart AND < windowEnd} (same date) against
 * its own {@code exam_order}, then hands the number back. Plain value object - not a
 * persisted shape.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotWindowBounds {

    /** Slot window start (inclusive) - the booking_time floored onto the grid. */
    private Date windowStart;

    /** Slot window end (exclusive) - the floor advanced by the granularity. */
    private Date windowEnd;
}
