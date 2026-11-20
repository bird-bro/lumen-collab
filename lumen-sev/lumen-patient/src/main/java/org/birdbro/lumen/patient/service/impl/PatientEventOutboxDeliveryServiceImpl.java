package org.birdbro.lumen.patient.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import org.birdbro.lumen.patient.entity.PatientEventOutbox;
import org.birdbro.lumen.patient.mapper.PatientEventOutboxMapper;
import org.birdbro.lumen.patient.service.PatientEventOutboxDeliveryService;
import org.springframework.stereotype.Service;

/**
 * Outbox delivery implementation (us-station-registration-sev task 2.2, design D4):
 * the pull is a PLAIN SELECT (no claim - re-pull visibility is the exactly-once-per-ack
 * contract), the ack is ONE guarded UPDATE ({@code id = ? AND status = PENDING}) whose
 * zero-row outcome IS the idempotent no-op success. No custom mapper statement is
 * added - the inherited MyBatis-Plus members keep the append-era reflection pin (the
 * outbox mapper declares no custom SQL) true.
 *
 * <p>Standalone statements need no {@code @Transactional} (the append precedent's
 * reasoning: a lone SELECT/UPDATE is atomic; the mapper joins a caller's transaction
 * when one is active).</p>
 *
 * @author birdbro
 */
@Service
public class PatientEventOutboxDeliveryServiceImpl implements PatientEventOutboxDeliveryService {

    private final PatientEventOutboxMapper patientEventOutboxMapper;

    public PatientEventOutboxDeliveryServiceImpl(PatientEventOutboxMapper patientEventOutboxMapper) {
        this.patientEventOutboxMapper = patientEventOutboxMapper;
    }

    @Override
    public List<PatientEventOutbox> pullPending(Integer limit) {
        int capped = (limit == null || limit <= 0) ? DEFAULT_PULL_LIMIT : limit;
        // LIMIT rides the int - no injection surface, no paging machinery needed
        return patientEventOutboxMapper.selectList(new LambdaQueryWrapper<PatientEventOutbox>()
                .eq(PatientEventOutbox::getStatus, STATUS_PENDING)
                .orderByAsc(PatientEventOutbox::getId)
                .last("LIMIT " + capped));
    }

    @Override
    public void ack(Integer id) {
        PatientEventOutbox patch = new PatientEventOutbox();
        patch.setStatus(STATUS_CONSUMED);
        // the status guard makes the ack idempotent: a non-PENDING (or missing) row
        // updates ZERO rows - the no-op success; the CONSUMED row stays for audit
        patientEventOutboxMapper.update(patch, new LambdaUpdateWrapper<PatientEventOutbox>()
                .eq(PatientEventOutbox::getId, id)
                .eq(PatientEventOutbox::getStatus, STATUS_PENDING));
    }
}
