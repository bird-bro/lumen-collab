package org.birdbro.lumen.setting.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.setting.dto.TempCreateRequest;
import org.birdbro.lumen.setting.dto.TempPageQuery;
import org.birdbro.lumen.setting.dto.TempUpdateRequest;
import org.birdbro.lumen.setting.entity.RepTemp;

/**
 * Report-template knowledge-base management (exam-setting-api task 5.1): the ONE-tree
 * model over {@code rep_temp} (flag 0 目录 / 1 模板) scoped per 检查类型 and split by
 * {@code type} (1 公共 / 2 个人). THE enforcement surface:
 *
 * <ul>
 *   <li>private (type=2) creates bind {@code owner_uid} SERVER-SIDE from the session's
 *       UserHeader - the request body's ownerUid is ignored;</li>
 *   <li>every type=2 read/write is owner-scoped - another user's private rows never
 *       render anywhere and their update/delete refuses A0400 without leaking
 *       content;</li>
 *   <li>public (type=1) rows ride the menu perms (the {@code @PreAuthorize} gates).</li>
 * </ul>
 *
 * <p>Interface since the 2026-11-13 all-services interface+impl ruling (the
 * {@code impl/RepTempServiceImpl} body is unchanged from the former concrete class;
 * the scope constants ride the interface contract).</p>
 *
 * @author birdbro
 */
public interface RepTempService {

    /** Scope value of the PUBLIC tree (the shared knowledge base). */
    int TYPE_PUBLIC = 1;
    /** Scope value of the PRIVATE tree (one user's own phrases). */
    int TYPE_PRIVATE = 2;

    /**
     * The scope's full tree for one modality: private requests resolve to the caller's
     * OWN rows only; public requests return the shared tree. The tree assembles
     * service-side from the up_id references (roots = up_id-less nodes).
     */
    List<Map<String, Object>> getTree(String modality, Integer type);

    /**
     * The templates (flag=1) under one parent node of one scope, name-filtered and
     * paged - the right-pane table feed.
     */
    IPage<RepTemp> getPage(TempPageQuery query);

    /**
     * Creates a tree node; for type=2 the owner binds from the SESSION (the request
     * body never decides ownership); returns the database-generated id.
     */
    Integer create(TempCreateRequest request);

    /**
     * Full-replaces the editable surface (D9 null-aware). Scope/ownership stay as
     * stored (they are not editable); a private row refuses a non-owner.
     */
    Boolean update(Integer id, TempUpdateRequest request);

    /** Logical delete; a private row refuses a non-owner. */
    Boolean delete(Integer id);
}
