package org.birdbro.lumen.manage.dto.deviceroom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The INTERNAL room-detail row (us-order-exam-room-sev task 2.2): the fields the
 * station's create-time validation reads - {@code did} (the station's department-match
 * check) and {@code enable} (the enabled check) - plus the identity trio. The bound
 * device summary is deliberately absent (the machine validates, it does not pick
 * devices).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoomInternalDetail {

    /** Room id. */
    private Integer id;

    /** Owning campus (org) id. */
    private Integer oid;

    /** Owning department id - the station-department match field. */
    private Integer did;

    /** Room name. */
    private String name;

    /** Enable flag (1 = enabled). */
    private Integer enable;
}
