package org.birdbro.lumen.manage.controller;

import java.util.List;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.manage.dto.deviceroom.DeviceCreateRequest;
import org.birdbro.lumen.manage.dto.deviceroom.DevicePageQuery;
import org.birdbro.lumen.manage.dto.deviceroom.DeviceUpdateRequest;
import org.birdbro.lumen.manage.entity.OrgDevice;
import org.birdbro.lumen.manage.service.OrgDeviceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Device registry endpoints (org-device-room-api task 2.2), the OPS-shaped
 * {@code /manage/org/device} family re-homed under the lumen console namespace. All
 * endpoints stay token-guarded by the copied security chain; the class gate is the
 * page perm, mutating methods pin their button perms (the OrgDepController shape).
 *
 * <p>The copied {@code GlobalResponseAdvice} wraps every return into the standard
 * envelope ({@code code:"200"}); mutation methods return {@code Boolean.TRUE} rather
 * than void ON PURPOSE - the copied advice renders a null body through its
 * {@code R.ofSuccess()} branch, whose code is "204". The {@code OrgDevice} ENTITY is a
 * safe response shape: it carries no secret fields.</p>
 *
 * @author birdbro
 */
@Api(tags = "设备档案")
// Page-level server-side gate (server-side-perm-enforcement shape).
@PreAuthorize("hasAuthority('system:device:list')")
@RestController
@RequestMapping("/api/v1/system/org-devices")
public class OrgDeviceController {

    private final OrgDeviceService orgDeviceService;

    public OrgDeviceController(OrgDeviceService orgDeviceService) {
        this.orgDeviceService = orgDeviceService;
    }

    /** Creates a device (references validated service-side); returns the new id. */
    @Log(title = "设备档案", type = "新增")
    @ApiOperation("创建设备")
    @PreAuthorize("hasAuthority('system:device:create')") // button-perm pin (D2 shape: the method annotation replaces the class page gate for this action)
    @PostMapping
    public Integer create(@Valid @RequestBody DeviceCreateRequest request) {
        return orgDeviceService.create(request);
    }

    /** Full-replace update (D9 null-aware: absent optionals clear). */
    @Log(title = "设备档案", type = "修改")
    @ApiOperation("设备全量更新")
    @PreAuthorize("hasAuthority('system:device:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody DeviceUpdateRequest request) {
        return orgDeviceService.update(id, request);
    }

    /** Logical delete, guarded by live room references (the room must rebind first). */
    @Log(title = "设备档案", type = "删除")
    @ApiOperation("逻辑删除设备")
    @PreAuthorize("hasAuthority('system:device:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return orgDeviceService.delete(id);
    }

    /** One device (the edit drawer's echo), or null when the id misses. */
    @ApiOperation("设备详情")
    @GetMapping("/{id}")
    public OrgDevice getById(@PathVariable Integer id) {
        return orgDeviceService.getById(id);
    }

    /** Picker feed for the room form: the org's devices, narrowed by depId when present. */
    @ApiOperation("设备列表（诊室绑定选择器，可按科室过滤）")
    @GetMapping("/list")
    public List<OrgDevice> list(@RequestParam Integer orgId,
            @RequestParam(required = false) Integer depId) {
        return orgDeviceService.getList(orgId, depId);
    }

    /** Paginated device table (org-scoped; type/status/keyword filters). */
    @ApiOperation("设备分页列表")
    @GetMapping("/page")
    public IPage<OrgDevice> page(DevicePageQuery query) {
        return orgDeviceService.getPage(query);
    }
}
