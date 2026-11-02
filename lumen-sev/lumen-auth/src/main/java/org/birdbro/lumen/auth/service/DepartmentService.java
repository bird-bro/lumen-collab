package org.birdbro.lumen.auth.service;

import java.util.List;

import org.birdbro.lumen.auth.dto.CreateDepartmentRequest;
import org.birdbro.lumen.auth.dto.UpdateDepartmentRequest;
import org.birdbro.lumen.auth.entity.Department;

/**
 * Department management (auth-rbac-foundation task 4.2): CRUD on the {@code department}
 * table that anchors medical workflows to the org tree. The {@code unitId} FK is
 * enforced SERVICE-side (explicit {@code A0400} envelope) with the database FK as the
 * backstop; {@code code} is duplicate-checked among live rows.
 *
 * @author birdbro
 */
public interface DepartmentService {

    /**
     * Creates a department for a unit node: the unit must exist (live) or the create is
     * rejected with {@code A0400}; a duplicate live {@code code} is rejected with
     * {@code A0712} (the partial unique index stays the backstop).
     *
     * @param request unit id + stable code + display name
     * @return the database-generated id of the new row
     */
    Integer create(CreateDepartmentRequest request);

    /**
     * Partial update: re-anchor to another unit / change code / rename (null fields
     * skipped). New values carry the same validations as create; an unknown target id
     * is rejected with {@code B0031}.
     *
     * @param id      the department to update
     * @param request the fields to change
     * @return {@code Boolean.TRUE} (envelope {@code "200"}; a void return would fall
     *         into the copied advice's null branch and render the {@code "204"} code)
     */
    Boolean update(Integer id, UpdateDepartmentRequest request);

    /**
     * Logical delete - delegated to MyBatis-Plus {@code deleteById}
     * ({@code @TableLogic} renders {@code UPDATE deleted = 1}).
     *
     * @param id the department to delete
     * @return {@code Boolean.TRUE} (see the update note on the envelope code)
     */
    Boolean delete(Integer id);

    /**
     * Lists live departments, optionally scoped to one unit ({@code unitId = null}
     * lists all). Ordered by id for a stable response.
     *
     * @param unitId the owning unit to scope by, or null for all units
     * @return the live department rows
     */
    List<Department> list(Integer unitId);
}
