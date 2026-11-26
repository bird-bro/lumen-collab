package org.birdbro.lumen.feign.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The INTERNAL room-list row on the Feign wire (us-order-exam-room-sev task 2.4): the
 * slim {@code {id, name}} projection the station room directory serves - the provider
 * side is lumen-system's {@code /api/v1/system/internal/org-rooms/list} (api delta
 * "Internal Exam Room Reads").
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoomSummaryDto {

    /** Room id - the station stamps this onto the order's room_id anchor. */
    private Integer id;

    /** Room name (the workbench/booking-dialog display label). */
    private String name;
}
