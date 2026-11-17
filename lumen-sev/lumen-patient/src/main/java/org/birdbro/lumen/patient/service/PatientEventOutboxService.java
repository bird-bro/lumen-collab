package org.birdbro.lumen.patient.service;

import java.util.Map;

/**
 * Transactional-outbox carriage (patient-domain task 3.5, design D2): rows land
 * PENDING inside the patient transaction and are ONLY ever INSERTed - APPEND-ONLY
 * BY INTERFACE. This interface exposes exactly ONE write method ({@code append});
 * there is no update, delete, or status-flip member anywhere on the module's outbox
 * path. The first consumer change (lumen-core snapshot refresh / exam-order repoint)
 * owns the poller that reads PENDING rows and will own their status machine; v1
 * publishes to NO message broker and runs NO poller.
 *
 * @author birdbro
 */
public interface PatientEventOutboxService {

    /** The archive was corrected (demographics update) - payload: the UPDATED archive. */
    String EVENT_PATIENT_CHANGED = "PATIENT_CHANGED";

    /** A minimal merge completed - payload: {@code {"survivorId":..,"mergedId":..}}. */
    String EVENT_PATIENT_MERGED = "PATIENT_MERGED";

    /**
     * Appends one PENDING outbox row inside the caller's transaction (a plain INSERT;
     * no annotation needed - the mapper joins the caller's transaction when one is
     * active, and a lone append auto-commits).
     *
     * @param eventType the event family ({@link #EVENT_PATIENT_CHANGED} /
     *        {@link #EVENT_PATIENT_MERGED} today)
     * @param patientId the affected archive id (for a merge event: the MERGED/absorbed
     *        id - the row whose {@code merged_into} pointer changed)
     * @param payload the event payload as a REAL jsonb object (serialized by
     *        {@code PatientJsonbTypeHandler}; a Map for the handler, never a
     *        pre-encoded JSON string)
     */
    void append(String eventType, Integer patientId, Map<String, Object> payload);
}
