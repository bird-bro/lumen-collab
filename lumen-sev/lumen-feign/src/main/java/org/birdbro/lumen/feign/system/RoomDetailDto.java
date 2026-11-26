package org.birdbro.lumen.feign.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The INTERNAL room-detail row on the Feign wire (us-order-exam-room-sev task 2.4):
 * the fields the station's create-time validation reads - {@code did} (the station's
 * department-match check) and {@code enable} (the enabled check) - plus the identity
 * trio. The provider side is lumen-system's
 * {@code /api/v1/system/internal/org-rooms/{id}}; a missing/deleted room answers
 * {@code data: null} (the adapter then refuses the create).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoomDetailDto {

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
