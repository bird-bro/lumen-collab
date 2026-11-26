package org.birdbro.lumen.manage.controller;

import java.util.List;

import org.birdbro.lumen.manage.dto.deviceroom.RoomInternalDetail;
import org.birdbro.lumen.manage.dto.deviceroom.RoomInternalSummary;
import org.birdbro.lumen.manage.service.OrgRoomService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;

/**
 * The INTERNAL exam-room reads (us-order-exam-room-sev task 2.2; spec delta api
 * "Internal Exam Room Reads") - the machine-facing rooms family for service-to-service
 * consumers (the US station room directory + create-time validation). Sits OUTSIDE the
 * JWT permission gates (the permit-all list) and behind the shared
 * {@code X-Internal-Key} interceptor (the {@code NumberIssueController} family
 * precedent - registered by {@code InternalRoomApiWebConfig}, a MANAGE-DOMAIN
 * configurer, NOT an edit of the copied framework WebMvcConfig); the console registry
 * endpoints ({@code /api/v1/system/org-rooms/**}) are UNTOUCHED and stay JWT +
 * {@code system:room:*} gated. Payloads ride the unified envelope via the copied
 * {@code GlobalResponseAdvice} (the {@code R<...>} wire shape the Feign clients read).
 *
 * @author birdbro
 */
@RestController
@RequestMapping("/api/v1/system/internal/org-rooms")
public class InternalRoomController {

    @Autowired
    private OrgRoomService orgRoomService;

    @ApiOperation("科室启用检查室列表（内部服务专用，X-Internal-Key 门控；id 升序）")
    @GetMapping("/list")
    public List<RoomInternalSummary> list(@RequestParam(value = "depId", required = false) Integer depId) {
        return orgRoomService.listInternalByDepartment(depId);
    }

    @ApiOperation("检查室校验字段（内部服务专用，X-Internal-Key 门控；缺失回 null）")
    @GetMapping("/{id}")
    public RoomInternalDetail detail(@PathVariable("id") Integer id) {
        return orgRoomService.getInternalById(id);
    }
}
