package org.birdbro.lumen.feign.system;

import java.util.Collections;
import java.util.List;

import org.birdbro.common.core.R;
import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;

import org.springframework.stereotype.Component;

/**
 * The SPLIT fallback of the system rooms internal contract (us-order-exam-room-sev
 * task 2.4): the two reads carry OPPOSITE degradation semantics, both deliberate —
 * the directory LIST read degrades to the HONEST EMPTY list (the desk sees an empty
 * room Select plus the station's load message; a required field with no options must
 * block, and an empty list blocks honestly), while the room DETAIL read — the
 * create-time VALIDATION read — fails CLOSED like the PatientApiFallback: a fabricated
 * room row would let an unverifiable create pass (检查室不存在或未启用 must be
 * answerable, never invented).
 *
 * @author birdbro
 */
@Component
public class SystemRoomApiFallback implements SystemRoomApi {

    @Override
    public R<List<RoomSummaryDto>> listInternal(Integer depId) {
        return R.ofSuccess(Collections.emptyList());
    }

    @Override
    public R<RoomDetailDto> getInternalRoom(Integer id) {
        throw new BusinessException(ExceptionEnum.ERROR_C0001,
                "检查室服务不可达，房间校验失败（fail-fast，绝不造房间）: " + id);
    }
}
