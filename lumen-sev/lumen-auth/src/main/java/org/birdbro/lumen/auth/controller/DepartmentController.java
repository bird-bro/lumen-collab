package org.birdbro.lumen.auth.controller;

import java.util.List;

import javax.validation.Valid;

import org.birdbro.lumen.auth.dto.CreateDepartmentRequest;
import org.birdbro.lumen.auth.dto.UpdateDepartmentRequest;
import org.birdbro.lumen.auth.entity.Department;
import org.birdbro.lumen.auth.service.DepartmentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Department management endpoints (auth-rbac-foundation task 4.2): CRUD on the
 * {@code department} table under the {@code /api/v1/auth} namespace nginx routes to
 * this service (Gateway Path Convention). All endpoints stay token-guarded by the
 * copied security chain ({@code anyRequest().authenticated()} - no
 * {@code WebSecurityCustomizer} addition).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}). Mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE: the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204" - returning a value keeps the
 * "200" success code the spec's scenarios use. The department ENTITY is a safe response
 * shape: it carries no secret fields (unlike {@code user_info}, which must go through a
 * DTO to keep the password hash out of the JSON).</p>
 *
 * @author birdbro
 */
@RestController
@RequestMapping("/api/v1/auth/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /** Creates a department for a unit node (FK validated service-side); returns the new id. */
    @PostMapping
    public Integer create(@Valid @RequestBody CreateDepartmentRequest request) {
        return departmentService.create(request);
    }

    /** Partial update: re-anchor / change code / rename (null fields skipped). */
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return departmentService.update(id, request);
    }

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return departmentService.delete(id);
    }

    /** Live departments, all when {@code unitId} is absent, else scoped to that unit. */
    @GetMapping("/list")
    public List<Department> list(@RequestParam(required = false) Integer unitId) {
        return departmentService.list(unitId);
    }
}
