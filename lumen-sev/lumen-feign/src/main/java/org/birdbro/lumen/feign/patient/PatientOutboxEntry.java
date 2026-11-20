package org.birdbro.lumen.feign.patient;

import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One transactional-outbox event entry (us-station-registration-sev task 2.1, design
 * D4/D5): the pull shape the patient domain hands out over the internal endpoint -
 * PENDING rows in id order, limit-capped, each carrying the RAW jsonb payload as a
 * string (the consumer parses it with its own mapper; the contract stays decoupled
 * from the patient schema's column types). An entry stays visible to re-pull until it
 * is acked; the ack flips its status to CONSUMED and KEEPS the row for audit.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatientOutboxEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The outbox row id (the ack's path parameter). */
    private Integer id;

    /** 事件类型（PATIENT_CHANGED / PATIENT_MERGED...）. */
    private String eventType;

    /** 事件载荷 - raw jsonb document as a string (the consumer parses it). */
    private String payload;

    /** Outbox row creation time. */
    private Date createTime;

    /** Outbox row last-update time (carries the consumption stamp after the ack). */
    private Date updateTime;
}
