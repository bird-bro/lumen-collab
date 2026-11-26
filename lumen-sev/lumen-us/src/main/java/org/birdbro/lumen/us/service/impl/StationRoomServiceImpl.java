package org.birdbro.lumen.us.service.impl;

import java.util.Collections;
import java.util.List;

import org.birdbro.lumen.feign.system.RoomSummaryDto;
import org.birdbro.lumen.feign.system.SystemRoomApi;
import org.birdbro.lumen.us.config.UsStationProperties;
import org.birdbro.lumen.us.service.StationRoomService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * {@link StationRoomService} implementation (us-order-exam-room-sev task 3.2): the
 * adapter call rides the Sentinel split fallback (list → empty, the recorded
 * degradation); this layer only scopes by the station binding and null-guards the
 * envelope - the fail-closed posture lives at BOTH ends (empty list here, the create
 * refusal in the registration path).
 *
 * @author birdbro
 */
@Service
public class StationRoomServiceImpl implements StationRoomService {

    private static final Logger log = LoggerFactory.getLogger(StationRoomServiceImpl.class);

    private final UsStationProperties stationProperties;
    private final SystemRoomApi systemRoomApi;

    @Autowired
    public StationRoomServiceImpl(UsStationProperties stationProperties,
            SystemRoomApi systemRoomApi) {
        this.stationProperties = stationProperties;
        this.systemRoomApi = systemRoomApi;
    }

    @Override
    public List<RoomSummaryDto> listStationRooms() {
        Long depId = stationProperties.getStationDeptId();
        if (depId == null) {
            // the unbound station: no department = no rooms (fail closed, the config
            // error is loud in the log - the desk sees the empty Select + message)
            log.error("lumen.us.station-dept-id is NOT configured - the room directory "
                    + "answers empty and order creation will be refused");
            return Collections.emptyList();
        }
        List<RoomSummaryDto> rooms = systemRoomApi.listInternal(depId.intValue()).getData();
        return rooms == null ? Collections.emptyList() : rooms;
    }
}
