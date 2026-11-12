package org.birdbro.framework.log.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.birdbro.common.Constants.Constants;
import org.birdbro.common.core.pojo.LogSign;
import org.birdbro.common.utils.DateUtils;
import org.birdbro.common.utils.SpringUtils;
import org.birdbro.framework.log.entity.LogInfoEntity;
import org.birdbro.framework.log.mapper.LogInfoMapper;
import org.birdbro.framework.log.service.LogSignInsertService;
import org.springframework.stereotype.Service;

/**
 * Sign-event write implementation (audit-log-unify-api b4; the former
 * {@code LogSignServiceImpl} reduced to its insert half — the query half died with
 * the dropped {@code public.log_sign} family, V25).
 *
 * <p>Sign-event write, retargeted to the ONE audit table (audit-log-unify-api design
 * D2/D5): the transport {@code LogSign} builds a {@code LogInfoEntity} — title = msg
 * (登录/登出), descr 1:1, {@code type} Integer PASSTHROUGH
 * (Constants.SUCCESS/FAIL/SIGN_OUT — no translation), uid/account/actionTime/ip/os/
 * browser/engine/mobile 1:1, and the credential token anchor lands in the existing
 * {@code session} column (D5) so the query side's select-exclusion covers it;
 * dateYm = current yyyymm, app = APP_CODE. No mapper of its own — it writes through
 * the shared {@link LogInfoMapper#insertInfo}.</p>
 *
 * @author birdbro
 * @since 2026-11-10
 */
@Slf4j
@Service
public class LogSignInsertServiceImpl implements LogSignInsertService {

    @Override
    public boolean insert(LogSign sign) {
        LogInfoEntity entity = LogInfoEntity.builder()
                .dateYm(Integer.parseInt(DateUtils.dateTimeNow("yyyyMM")))
                .app(Constants.APP_CODE.toString())
                .title(sign.getMsg())
                .descr(sign.getDescr())
                .type(sign.getType())
                .uid(sign.getUid())
                .account(sign.getAccount())
                .actionTime(sign.getActionTime())
                .mobile(sign.getMobile())
                .ip(sign.getIp())
                .os(sign.getOs())
                .browser(sign.getBrowser())
                .engine(sign.getEngine())
                .session(sign.getToken())
                .build();
        SpringUtils.getBean(LogInfoMapper.class).insertInfo(entity);
        return true;
    }
}
