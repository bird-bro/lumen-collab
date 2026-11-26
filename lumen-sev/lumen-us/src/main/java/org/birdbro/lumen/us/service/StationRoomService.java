package org.birdbro.lumen.us.service;

import java.util.List;

import org.birdbro.lumen.feign.system.RoomSummaryDto;

/**
 * The station room directory (us-order-exam-room-sev task 3.2; spec delta us-station
 * "Station Room Directory"): the station department's enabled rooms for the workbench +
 * booking-dialog room pickers. Interface per the 2026-11-13 all-services ruling.
 *
 * @author birdbro
 */
public interface StationRoomService {

    /**
     * The station department's rooms as the slim {@code {id, name}} rows: the knob
     * {@code lumen.us.station-dept-id} scopes the read; an absent binding or an adapter
     * failure answers the HONEST EMPTY list (fail closed - the desk is blocked, never
     * offered a fabricated registry).
     */
    List<RoomSummaryDto> listStationRooms();
}
