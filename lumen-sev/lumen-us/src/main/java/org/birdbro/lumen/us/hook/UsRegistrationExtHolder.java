package org.birdbro.lumen.us.hook;

import org.birdbro.lumen.us.entity.UsRegistrationExt;

/**
 * The request-thread stash that carries the OPTIONAL ext block from the create path into
 * the kernel {@code RegistrationHook} seam (us-station-registration-sev task 3.2, design
 * D7). WHY it exists: the kernel's {@code RegistrationContext} carries exactly the
 * persisted aggregate (orderId/order/execution/report) - the kernel deliberately has NO
 * department-field carrier, and the kernel stays UNTOUCHED - so the us create path
 * stashes the ext block thread-locally BEFORE calling {@code registerOrder}, and the us
 * hook DRAINS it inside the registration transaction (same servlet thread, deterministic
 * handoff). The create path clears the stash in a {@code finally} block, so a refused or
 * failed registration never leaks its ext block into a later request on the pooled
 * thread.
 *
 * <p>Ext blocks ride this holder ONLY around the one {@code registerOrder} call of the
 * create path - the HIS two-phase confirm path (which never sets it) creates orders with
 * no extension row, exactly as its payload dictates.</p>
 *
 * @author birdbro
 */
public final class UsRegistrationExtHolder {

    private static final ThreadLocal<UsRegistrationExt> PENDING = new ThreadLocal<>();

    private UsRegistrationExtHolder() {
    }

    /** Stashes the create body's ext block for the registration transaction to drain. */
    public static void set(UsRegistrationExt ext) {
        PENDING.set(ext);
    }

    /**
     * Drains the stashed ext block (one-shot - the stash is emptied on read, so a hook
     * can never persist it twice). {@code null} = this registration carries no ext block.
     */
    public static UsRegistrationExt poll() {
        UsRegistrationExt ext = PENDING.get();
        PENDING.remove();
        return ext;
    }

    /** Clears any leftover stash (the create path's {@code finally} safety net). */
    public static void clear() {
        PENDING.remove();
    }
}
