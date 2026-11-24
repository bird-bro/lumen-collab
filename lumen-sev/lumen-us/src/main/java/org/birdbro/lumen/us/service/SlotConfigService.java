package org.birdbro.lumen.us.service;

import java.util.List;

import org.birdbro.lumen.us.dto.SlotConfigRequest;
import org.birdbro.lumen.us.dto.SlotConfigView;

/**
 * The slot-config administration service (us-registration-redesign-api tasks 4.1-4.3,
 * design D2): the CRUD surface for the per-modality pre-registration policy the kernel
 * capacity validation consumes. The config rows live in the US SCHEMA (the ① D3
 * department ownership; the D7 FOR UPDATE serialization requires same-schema) - this
 * service is the schema owner's admin surface. One LIVE row per modality (the kernel
 * live-unique index backstops the service guard); the modality token is the pool
 * identity and NEVER changes after create; delete is soft (the ①-fixed DDL shape
 * carries no enable column - edit + soft-delete only).
 *
 * @author birdbro
 */
public interface SlotConfigService {

    /** Lists the LIVE configs, windows decoded as structured pairs. */
    List<org.birdbro.lumen.us.dto.SlotConfigView> list();

    /**
     * Creates one config: the token stores trimmed verbatim; a duplicate live modality
     * refuses A0400 naming the duplicate.
     */
    org.birdbro.lumen.us.dto.SlotConfigView create(SlotConfigRequest request);

    /**
     * Updates the policy fields (granularity/capacity/windows); a changed modality
     * token refuses A0400 (the pool identity is immutable).
     */
    org.birdbro.lumen.us.dto.SlotConfigView update(Integer id, SlotConfigRequest request);

    /** Soft-deletes the config; the modality's pre-registration then refuses fail-closed. */
    void delete(Integer id);
}
