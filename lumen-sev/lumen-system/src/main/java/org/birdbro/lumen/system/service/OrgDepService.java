package org.birdbro.lumen.system.service;

import java.util.List;

import org.birdbro.lumen.system.dto.CreateOrgDepRequest;
import org.birdbro.lumen.system.dto.UpdateOrgDepRequest;
import org.birdbro.lumen.system.entity.OrgDep;

/**
 * OrgDep management (auth-rbac-foundation task 4.2): CRUD on the {@code department}
 * table that anchors medical workflows to the org tree. The {@code orgId} FK is
 * enforced SERVICE-side (explicit {@code A0400} envelope) with the database FK as the
 * backstop; {@code code} is duplicate-checked among live rows.
 *
 * @author birdbro
 */
public interface OrgDepService {

    /**
     * Creates a department for a org node: the org must exist (live) or the create is
     * rejected with {@code A0400}; a duplicate live {@code code} is rejected with
     * {@code A0712} (the partial unique index stays the backstop).
     *
     * @param request org id + stable code + display name
     * @return the database-generated id of the new row
     */
    Integer create(CreateOrgDepRequest request);

    /**
     * Partial update: re-anchor to another org / change code / rename (null fields
     * skipped). New values carry the same validations as create; an unknown target id
     * is rejected with {@code B0031}.
     *
     * @param id      the department to update
     * @param request the fields to change
     * @return {@code Boolean.TRUE} (envelope {@code "200"}; a void return would fall
     *         into the copied advice's null branch and render the {@code "204"} code)
     */
    Boolean update(Integer id, UpdateOrgDepRequest request);

    /**
     * Logical delete - delegated to MyBatis-Plus {@code deleteById}
     * ({@code @TableLogic} renders {@code UPDATE deleted = 1}).
     *
     * @param id the department to delete
     * @return {@code Boolean.TRUE} (see the update note on the envelope code)
     */
    Boolean delete(Integer id);

    /**
     * Lists live departments, optionally scoped to one org ({@code orgId = null}
     * lists all). Ordered by id for a stable response.
     *
     * @param orgId the owning org to scope by, or null for all orgs
     * @return the live department rows
     */
    List<OrgDep> list(Integer orgId);
}
