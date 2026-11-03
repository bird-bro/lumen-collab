package org.birdbro.lumen.auth.controller;

import java.util.List;

import javax.validation.Valid;

import org.birdbro.lumen.auth.dto.CreateOrgDepRequest;
import org.birdbro.lumen.auth.dto.UpdateOrgDepRequest;
import org.birdbro.lumen.auth.entity.OrgDep;
import org.birdbro.lumen.auth.service.OrgDepService;
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
 * OrgDep management endpoints (auth-rbac-foundation task 4.2): CRUD on the
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
@RequestMapping("/api/v1/auth/org-deps")
public class OrgDepController {

    private final OrgDepService orgDepService;

    public OrgDepController(OrgDepService orgDepService) {
        this.orgDepService = orgDepService;
    }

    /** Creates a department for a org node (FK validated service-side); returns the new id. */
    @PostMapping
    public Integer create(@Valid @RequestBody CreateOrgDepRequest request) {
        return orgDepService.create(request);
    }

    /** Partial update: re-anchor / change code / rename (null fields skipped). */
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id, @Valid @RequestBody UpdateOrgDepRequest request) {
        return orgDepService.update(id, request);
    }

    /** Logical delete ({@code @TableLogic}: {@code UPDATE deleted = 1}). */
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return orgDepService.delete(id);
    }

    /** Live departments, all when {@code orgId} is absent, else scoped to that org. */
    @GetMapping("/list")
    public List<OrgDep> list(@RequestParam(required = false) Integer orgId) {
        return orgDepService.list(orgId);
    }
}
