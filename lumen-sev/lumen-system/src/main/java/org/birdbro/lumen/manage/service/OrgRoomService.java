package org.birdbro.lumen.manage.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.manage.dto.deviceroom.RoomCreateRequest;
import org.birdbro.lumen.manage.dto.deviceroom.RoomPageQuery;
import org.birdbro.lumen.manage.dto.deviceroom.RoomRow;
import org.birdbro.lumen.manage.dto.deviceroom.RoomStatusRequest;
import org.birdbro.lumen.manage.dto.deviceroom.RoomUpdateRequest;
import org.birdbro.lumen.manage.entity.OrgRoom;

/**
 * Exam-room registry management (org-device-room-api task 3.2): CRUD over
 * {@code org_room} under {@code /api/v1/system/org-rooms}. The room is a BUSINESS
 * place: it references its device by the rebindable {@code dev_id} and NEVER copies
 * device fields (the split ruling); reads resolve the device summary by the mapper's
 * LEFT JOIN (D2, no N+1). References are SERVICE-enforced (D3): live org, live
 * same-org department, live same-org device; the name is unique within the owning
 * department among live rows (D4). Updates are D9 NULL-AWARE full-replaces (absent
 * optionals clear, incl. the {@code dev_id} binding); the status toggle is a D6
 * dedicated command.
 *
 * <p>Error-code mapping: every registry violation is {@code A0400} with a descriptive
 * message (the spec's uniform rejection code for this family).</p>
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/OrgRoomServiceImpl} body is unchanged from the former concrete class;
 * the ruling supersedes the concrete-class reading of the service-package-layout
 * decision cited here before).</p>
 *
 * @author birdbro
 */
public interface OrgRoomService {

    /**
     * Paginated room table with the joined device summary (D10: a present orgId
     * filters to that campus, an absent one lists every campus); id descending.
     */
    IPage<RoomRow> getPage(RoomPageQuery query);

    /** Picker/feed list: the org's (optionally one department's) live rooms, join-free. */
    List<OrgRoom> getList(Integer orgId, Integer depId);

    /**
     * One room with the bound-device summary resolved server-side, or null when the id
     * misses a live row.
     */
    RoomRow getById(Integer id);

    /**
     * Creates a room (dead/foreign oid/did/devId and duplicate name-in-department
     * reject A0400, no row written); returns the database-generated id.
     */
    Integer create(RoomCreateRequest request);

    /**
     * Full-replaces the editable surface (D9 null-aware), including REBIND (a new live
     * same-org {@code devId}) and CLEAR ({@code devId} absent); a missing room id
     * rejects A0400.
     */
    Boolean update(Integer id, RoomUpdateRequest request);

    /** Toggles only {@code enable} (D6 dedicated command); a missing id rejects A0400. */
    Boolean setStatus(Integer id, RoomStatusRequest request);

    /**
     * Logically deletes a room (no downstream rows exist yet - the workstation feature
     * adds its own guard); a missing room id rejects A0400.
     */
    Boolean delete(Integer id);
}
