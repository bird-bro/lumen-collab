package org.birdbro.lumen.core.hooks;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.birdbro.lumen.core.entity.ExamExecution;
import org.birdbro.lumen.core.entity.ExamOrder;
import org.birdbro.lumen.core.entity.ExamReport;

/**
 * The registration-hook execution context (lumen-core-registration-kernel task 7.1):
 * the freshly persisted aggregate, handed to every registered hook inside the
 * registration transaction. Department side-effects (PACS worklist push, 叫号, 分单,
 * HIS outbound - the EIS five-step linkage) hang off this context; SLOW outbound
 * integrations must ride the consumer's own async/outbox path, never block this
 * transaction (design D8).
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationContext {

    /** The persisted order id. */
    private Integer orderId;

    /** The persisted order document. */
    private ExamOrder order;

    /** The persisted execution member. */
    private ExamExecution execution;

    /** The persisted report member. */
    private ExamReport report;
}
