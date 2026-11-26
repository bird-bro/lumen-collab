package org.birdbro.lumen.us.controller;

import java.util.List;

import org.birdbro.lumen.feign.system.RoomSummaryDto;
import org.birdbro.lumen.us.service.StationRoomService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;

/**
 * The station room directory (us-order-exam-room-sev task 3.2; spec delta us-station
 * "Station Room Directory"): {@code GET /api/v1/us/rooms} behind {@code us:order:create}
 * - the room pick is a create-time decision, so the READ perm is the CREATE perm (the
 * {@code us:order:*} family's first read-under-create-perm endpoint). The payload is
 * the slim {@code {id, name}} row list; the failure/absent-binding band is the empty
 * list (the degradation contract lives in the service, the envelope wraps here).
 *
 * @author birdbro
 */
@RestController
@RequestMapping("/api/v1/us")
public class StationRoomController {

    @Autowired
    private StationRoomService stationRoomService;

    @ApiOperation("站内检查室目录（本站科室启用房间，id 升序；us:order:create 门控）")
    @PreAuthorize("hasAuthority('us:order:create')")
    @GetMapping("/rooms")
    public List<RoomSummaryDto> rooms() {
        return stationRoomService.listStationRooms();
    }
}
