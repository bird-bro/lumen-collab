package org.birdbro.lumen.manage.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.manage.dto.deviceroom.DeviceCreateRequest;
import org.birdbro.lumen.manage.dto.deviceroom.DevicePageQuery;
import org.birdbro.lumen.manage.dto.deviceroom.DeviceUpdateRequest;
import org.birdbro.lumen.manage.entity.OrgDevice;

/**
 * Device registry management (org-device-room-api task 2.2): CRUD over
 * {@code org_device} under {@code /api/v1/system/org-devices}. Every device is
 * org-scoped; references are SERVICE-enforced (zero physical FKs): {@code oid} must hit
 * a live org, a present {@code did} a live department of the SAME org, the {@code code}
 * unique within the org among live rows (D3/D4). Updates are D9 NULL-AWARE
 * full-replaces - absent optional fields CLEAR (an explicit wrapper update, never the
 * null-skipping updateById). The delete guard counts LIVE room references and names the
 * count in the message - the room must rebind before its device can go.
 *
 * <p>Error-code mapping: every registry violation is {@code A0400} with a descriptive
 * message (the spec's uniform rejection code for this family).</p>
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/OrgDeviceServiceImpl} body is unchanged from the former concrete class;
 * the ruling supersedes the concrete-class reading of the service-package-layout
 * decision cited here before).</p>
 *
 * @author birdbro
 */
public interface OrgDeviceService {

    /**
     * Paginated device table (D10, second-round ruling): a present {@code orgId}
     * filters to that campus, an ABSENT one lists every campus (the tree's
     * hospital-root selection shows the whole estate); type/status dict filters and
     * the name-or-code keyword apply on top. Ordered id-descending.
     */
    IPage<OrgDevice> getPage(DevicePageQuery query);

    /**
     * Picker feed for the room form (D10): the org's live devices, name-ordered,
     * narrowed to one department when {@code depId} is present (the picker feeds from
     * the room's own 负责科室).
     */
    List<OrgDevice> getList(Integer orgId, Integer depId);

    /** One device, or null when the id misses a live row. */
    OrgDevice getById(Integer id);

    /**
     * Creates a device (duplicate live code / dead or foreign department reject A0400,
     * no row written); returns the database-generated id of the new row.
     */
    Integer create(DeviceCreateRequest request);

    /** Full-replaces the editable surface (D9 null-aware); a missing id rejects A0400. */
    Boolean update(Integer id, DeviceUpdateRequest request);

    /**
     * Logically deletes a device; a device still referenced by any live room rejects
     * A0400 with the room count in the message (the room must rebind first).
     */
    Boolean delete(Integer id);
}
