package org.birdbro.lumen.core.hooks;

/**
 * The ordered registration extension hook (lumen-core-registration-kernel task 7.1;
 * spec exam-workflow-kernel "Registration Extension Hooks"). Consuming services
 * register beans; the kernel registration transaction fires them in {@link #order()}
 * sequence after the aggregate persists, and ANY exception propagates - the whole
 * registration rolls back, so no partial order survives (the EIS five-step linkage
 * semantics: 推 PACS worklist / 写叫号 / 云胶片 / 分单 / 推 HIS all live OUTSIDE the
 * kernel as consumer hooks).
 *
 * <p>Contract notes for implementers: run fast, in-process work here; slow outbound
 * integrations (HIS push, PACS) belong in the consumer's own async/outbox path - a
 * slow remote call inside a hook holds the registration transaction open (design D8).</p>
 *
 * @author birdbro
 */
public interface RegistrationHook {

    /**
     * The firing order - lower runs earlier; ties keep an unspecified relative order.
     */
    int order();

    /**
     * Executes inside the registration transaction.
     *
     * @param context the freshly persisted aggregate
     */
    void execute(RegistrationContext context);
}
