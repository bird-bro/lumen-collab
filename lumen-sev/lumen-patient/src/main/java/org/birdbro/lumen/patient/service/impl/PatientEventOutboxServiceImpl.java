package org.birdbro.lumen.patient.service.impl;

import java.util.Map;

import org.birdbro.lumen.patient.entity.PatientEventOutbox;
import org.birdbro.lumen.patient.mapper.PatientEventOutboxMapper;
import org.birdbro.lumen.patient.service.PatientEventOutboxService;
import org.springframework.stereotype.Service;

/**
 * Outbox append implementation (patient-domain task 3.5): the module's ONLY outbox
 * writer, and a pure INSERT - rows land with {@code status = PENDING} (set here so
 * the contract is visible in code, not merely a DB default) and no code path in the
 * module ever updates or deletes them.
 *
 * @author birdbro
 */
@Service
public class PatientEventOutboxServiceImpl implements PatientEventOutboxService {

    /** The only status v1 ever writes (consumers own the state machine later). */
    private static final String STATUS_PENDING = "PENDING";

    private final PatientEventOutboxMapper patientEventOutboxMapper;

    public PatientEventOutboxServiceImpl(PatientEventOutboxMapper patientEventOutboxMapper) {
        this.patientEventOutboxMapper = patientEventOutboxMapper;
    }

    @Override
    public void append(String eventType, Integer patientId, Map<String, Object> payload) {
        patientEventOutboxMapper.insert(PatientEventOutbox.builder()
                .eventType(eventType)
                .patientId(patientId)
                .payload(payload)
                .status(STATUS_PENDING)
                .build());
    }
}
