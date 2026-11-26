package org.birdbro.lumen.manage.dto.deviceroom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The INTERNAL room-list row (us-order-exam-room-sev task 2.2): the machine-facing
 * slim projection ({id, name} only) the station room directory serves - the picker
 * reads rooms, not devices (no device join payload).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoomInternalSummary {

    /** Room id - the station stamps this onto the order's room_id anchor. */
    private Integer id;

    /** Room name (the workbench/booking-dialog display label). */
    private String name;
}
