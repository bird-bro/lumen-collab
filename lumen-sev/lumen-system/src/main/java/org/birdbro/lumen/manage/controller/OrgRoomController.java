package org.birdbro.lumen.manage.controller;

import java.util.List;

import javax.validation.Valid;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.birdbro.common.annotation.Log;

import org.birdbro.lumen.manage.dto.deviceroom.RoomCreateRequest;
import org.birdbro.lumen.manage.dto.deviceroom.RoomPageQuery;
import org.birdbro.lumen.manage.dto.deviceroom.RoomRow;
import org.birdbro.lumen.manage.dto.deviceroom.RoomStatusRequest;
import org.birdbro.lumen.manage.dto.deviceroom.RoomUpdateRequest;
import org.birdbro.lumen.manage.entity.OrgRoom;
import org.birdbro.lumen.manage.service.OrgRoomService;
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
 * Exam-room registry endpoints (org-device-room-api task 3.2), the OPS-shaped
 * {@code /manage/org/room} family re-modeled per the split ruling: the room page rows
 * and detail carry the bound-device summary resolved server-side (the join lives in
 * {@code OrgRoomMapper}), and the room form never edits device access fields. Class
 * gate + button-perm pins follow the OrgDepController shape; the status toggle pins
 * its own {@code system:room:status}.
 *
 * @author birdbro
 */
@Api(tags = "诊室配置")
// Page-level server-side gate (server-side-perm-enforcement shape).
@PreAuthorize("hasAuthority('system:room:list')")
@RestController
@RequestMapping("/api/v1/system/org-rooms")
public class OrgRoomController {

    private final OrgRoomService orgRoomService;

    public OrgRoomController(OrgRoomService orgRoomService) {
        this.orgRoomService = orgRoomService;
    }

    /** Creates a room (references validated service-side); returns the new id. */
    @Log(title = "诊室配置", type = "新增")
    @ApiOperation("创建诊室（可绑定设备）")
    @PreAuthorize("hasAuthority('system:room:create')") // button-perm pin
    @PostMapping
    public Integer create(@Valid @RequestBody RoomCreateRequest request) {
        return orgRoomService.create(request);
    }

    /** Full-replace update (D9 null-aware; rebind or clear the device binding). */
    @Log(title = "诊室配置", type = "修改")
    @ApiOperation("诊室全量更新（含换绑/解绑设备）")
    @PreAuthorize("hasAuthority('system:room:update')") // button-perm pin
    @PutMapping("/{id}")
    public Boolean update(@PathVariable Integer id,
            @Valid @RequestBody RoomUpdateRequest request) {
        return orgRoomService.update(id, request);
    }

    /** Dedicated status command (D6): only {@code enable} changes. */
    @Log(title = "诊室配置", type = "修改")
    @ApiOperation("启用/停用诊室")
    @PreAuthorize("hasAuthority('system:room:status')") // button-perm pin
    @PutMapping("/{id}/status")
    public Boolean setStatus(@PathVariable Integer id,
            @Valid @RequestBody RoomStatusRequest request) {
        return orgRoomService.setStatus(id, request);
    }

    /** Logical delete. */
    @Log(title = "诊室配置", type = "删除")
    @ApiOperation("逻辑删除诊室")
    @PreAuthorize("hasAuthority('system:room:delete')") // button-perm pin
    @DeleteMapping("/{id}")
    public Boolean delete(@PathVariable Integer id) {
        return orgRoomService.delete(id);
    }

    /** One room with the bound-device summary (the edit drawer's echo). */
    @ApiOperation("诊室详情（含绑定设备摘要）")
    @GetMapping("/{id}")
    public RoomRow getById(@PathVariable Integer id) {
        return orgRoomService.getById(id);
    }

    /** Live rooms of one org (optionally one department), join-free. */
    @ApiOperation("诊室列表")
    @GetMapping("/list")
    public List<OrgRoom> list(@RequestParam Integer orgId,
            @RequestParam(required = false) Integer depId) {
        return orgRoomService.getList(orgId, depId);
    }

    /** Paginated room table with the joined device summary. */
    @ApiOperation("诊室分页列表")
    @GetMapping("/page")
    public IPage<RoomRow> page(RoomPageQuery query) {
        return orgRoomService.getPage(query);
    }
}
