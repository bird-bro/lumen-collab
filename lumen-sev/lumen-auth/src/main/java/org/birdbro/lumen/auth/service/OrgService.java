package org.birdbro.lumen.auth.service;

import java.util.List;

import org.birdbro.lumen.auth.dto.CreateOrgRequest;
import org.birdbro.lumen.auth.dto.UpdateOrgRequest;
import org.birdbro.lumen.auth.entity.OrgInfo;

/**
 * Org management (auth-rbac-foundation task 4.1): CRUD on the self-referencing
 * {@code org_info} org tree plus the tree query. Tree integrity is SERVICE-level (the
 * schema deliberately carries no self-FK): a non-root {@code upId} must reference an
 * existing live org, so an orphan can never enter the tree through the API.
 *
 * @author birdbro
 */
public interface OrgService {

    /**
     * Creates a org row. {@code upId = 0} creates a root (multi-root capable); a
     * non-zero {@code upId} must reference an existing live org or the create is
     * rejected with {@code A0400} (referenced parent missing). An omitted
     * {@code level} is derived from the parent ({@code parent.level + 1}, roots 1).
     *
     * @param request parent id + optional level/type + display name
     * @return the database-generated id of the new row
     */
    Integer create(CreateOrgRequest request);

    /**
     * Partial update (rename / re-parent / level / type): {@code null} request fields
     * are skipped. Re-parenting validates the new parent (exists, not the org itself).
     *
     * @param id      the org to update
     * @param request the fields to change
     * @return {@code Boolean.TRUE} (envelope {@code "200"}; a void return would fall
     *         into the copied advice's null branch and render the {@code "204"} code)
     */
    Boolean update(Integer id, UpdateOrgRequest request);

    /**
     * Logical delete - delegated to MyBatis-Plus {@code deleteById}, whose
     * {@code @TableLogic} renders it as {@code UPDATE deleted = 1}; the partial unique
     * landscape keeps nothing blocked behind the dead row.
     *
     * @param id the org to delete
     * @return {@code Boolean.TRUE} (see the update note on the envelope code)
     */
    Boolean delete(Integer id);

    /**
     * The full org tree via the Batch 2 {@link OrgTreeService} assembly: roots are the
     * {@code upId = 0} rows, children nest by {@code upId}, orphans drop.
     *
     * @return the root orgs with their nested children
     */
    List<OrgInfo> tree();
}
