package org.birdbro.lumen.feign.system;

import java.util.List;

import org.birdbro.common.core.R;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The system exam-room INTERNAL Feign contract (us-order-exam-room-sev task 2.4): the
 * machine-facing rooms reads the US station consumes for its room directory
 * ({@code GET /api/v1/us/rooms}) and its create-time room validation. The provider is
 * lumen-system's internal family {@code /api/v1/system/internal/org-rooms/**} (api
 * delta "Internal Exam Room Reads") - X-Internal-Key guarded (the NumberIssueController
 * precedent; the console registry endpoints stay JWT + {@code system:room:*} gated and
 * are NOT this contract). The client REUSES the existing {@code lumen.feign.system.url}
 * target key (the {@code NumberApi} family - no new config family) and the consumer's
 * context-wide {@code X-Internal-Key} request interceptor (both lumen-feign clients
 * share one auth scheme). Consumers enable it with {@code @EnableFeignClients}.
 *
 * <p>SPLIT fallback semantics: the LIST read degrades to an HONEST EMPTY list (a
 * required field with no options blocks the desk - the load failure surfaces as a
 * station message); the DETAIL read (the create-time VALIDATION read) fails CLOSED -
 * a fabricated room row would let an unverifiable create pass, which is FORBIDDEN
 * (the PatientApiFallback precedent).</p>
 *
 * @author birdbro
 */
@FeignClient(name = "lumen-system-rooms", url = "${lumen.feign.system.url:}",
        fallback = SystemRoomApiFallback.class)
public interface SystemRoomApi {

    /**
     * Lists the department's ENABLED rooms as the slim {@code {id, name}} projection,
     * ordered by id (the directory feed; a null/absent department answers empty).
     *
     * @param depId the station's configured department id
     * @return the envelope carrying {@code data} = the slim room rows
     */
    @GetMapping("/api/v1/system/internal/org-rooms/list")
    R<List<RoomSummaryDto>> listInternal(@RequestParam("depId") Integer depId);

    /**
     * Reads one room's validation fields ({@code did}/{@code enable}); a missing or
     * logically-deleted room answers {@code data: null}.
     *
     * @param id the room id
     * @return the envelope carrying {@code data} = the validation fields, or null data
     */
    @GetMapping("/api/v1/system/internal/org-rooms/{id}")
    R<RoomDetailDto> getInternalRoom(@PathVariable("id") Integer id);
}
