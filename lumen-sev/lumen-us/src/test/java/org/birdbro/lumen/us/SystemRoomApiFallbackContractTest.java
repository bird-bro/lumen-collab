package org.birdbro.lumen.us;

import java.util.List;

import org.birdbro.common.exception.BusinessException;
import org.birdbro.lumen.feign.system.RoomSummaryDto;
import org.birdbro.lumen.feign.system.SystemRoomApi;
import org.birdbro.lumen.feign.system.SystemRoomApiFallback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link SystemRoomApi} fallback contract proof (us-order-exam-room-sev task 2.3):
 * the SPLIT fallback semantics — the directory LIST read degrades to an HONEST EMPTY
 * list (a required field with no options blocks the desk; the load failure surfaces at
 * the station), while the room DETAIL read (the create-time VALIDATION read) fails
 * CLOSED — a fabricated room row would let an unverifiable create pass, which is
 * FORBIDDEN (the PatientApiFallback fail-fast precedent). Plain unit test — the
 * fallback is a pure {@code @Component}, no Spring context needed.
 *
 * @author birdbro
 */
class SystemRoomApiFallbackContractTest {

    @Test
    void listFallbackAnswersTheHonestEmptyList() {
        SystemRoomApi fallback = new SystemRoomApiFallback();
        List<RoomSummaryDto> rooms = fallback.listInternal(307).getData();
        assertTrue(rooms != null && rooms.isEmpty(),
                "the list fallback degrades to the empty list - the desk sees an empty "
                + "room Select and the load failure surfaces as a station message");
    }

    @Test
    void listFallbackAnswersEmptyEvenWithoutADepartment() {
        SystemRoomApi fallback = new SystemRoomApiFallback();
        assertEquals(0, fallback.listInternal(null).getData().size(),
                "the fallback never fabricates rows regardless of the argument");
    }

    @Test
    void detailFallbackFailsClosed() {
        SystemRoomApi fallback = new SystemRoomApiFallback();
        assertThrows(BusinessException.class, () -> fallback.getInternalRoom(307),
                "the detail (validation) fallback THROWS - a fabricated room row must "
                + "never let an unverifiable create pass");
        assertThrows(BusinessException.class, () -> fallback.getInternalRoom(null),
                "the fail-closed contract holds on every path");
    }
}
