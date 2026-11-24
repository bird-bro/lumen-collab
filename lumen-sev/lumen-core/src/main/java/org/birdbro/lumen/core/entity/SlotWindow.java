package org.birdbro.lumen.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One open time window of the modality's slot config (kernel-pre-registration design
 * D3): a {@code {start,end}} pair of {@code HH:mm} strings carried inside the
 * {@code exam_slot_config.open_windows} jsonb array. Containment is
 * {@code start <= time < end} (end exclusive - a booking at exactly the window end
 * belongs to no window). An EMPTY array = the modality has no open windows =
 * pre-registration refused (opt-in per modality, the safe default).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SlotWindow {

    /** Window start, {@code HH:mm} (inclusive). */
    private String start;

    /** Window end, {@code HH:mm} (exclusive). */
    private String end;
}
