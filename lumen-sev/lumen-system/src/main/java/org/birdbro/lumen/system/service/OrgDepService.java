package org.birdbro.lumen.system.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import org.birdbro.lumen.system.dto.CreateOrgDepRequest;
import org.birdbro.lumen.system.dto.OrgDepPageQuery;
import org.birdbro.lumen.system.dto.UpdateOrgDepRequest;
import org.birdbro.lumen.system.entity.OrgDep;

/**
 * Campus-scoped department management (org-management-redesign): CRUD on {@code org_dep}
 * plus the paginated table query. Departments anchor to live, enabled campuses; codes are
 * immutable and campus-scoped.
 *
 * @author birdbro
 */
public interface OrgDepService {

    /**
     * Creates a department for a live, enabled campus.
     *
     * @param request campus id + immutable code + display name + optional status
     * @return the database-generated id of the new row
     */
    Integer create(CreateOrgDepRequest request);

    /**
     * Partially updates a department. The code must match the stored code when present;
     * the target campus must be live and enabled.
     *
     * @param id      the department to update
     * @param request the fields to change
     * @return {@code Boolean.TRUE}
     */
    Boolean update(Integer id, UpdateOrgDepRequest request);

    /**
     * Logically deletes a department.
     *
     * @param id the department to delete
     * @return {@code Boolean.TRUE}
     */
    Boolean delete(Integer id);

    /**
     * Legacy list contract: all live departments, or one org's rows when an owner is
     * supplied.
     *
     * @param orgId the owning org to scope by, or null for all orgs
     * @return the live department rows
     */
    List<OrgDep> list(Integer orgId);

    /**
     * Paginated department table query with optional campus, keyword, and status filters.
     *
     * @param query page and filter parameters
     * @return the deterministic department page
     */
    IPage<OrgDep> page(OrgDepPageQuery query);
}
