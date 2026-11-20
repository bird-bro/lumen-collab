package org.birdbro.lumen.us.hook;

import org.birdbro.lumen.core.hooks.RegistrationContext;
import org.birdbro.lumen.core.hooks.RegistrationHook;
import org.birdbro.lumen.us.entity.UsRegistrationExt;
import org.birdbro.lumen.us.mapper.UsRegistrationExtMapper;
import org.springframework.stereotype.Component;

/**
 * The us {@link RegistrationHook} implementation (us-station-registration-sev task 3.2,
 * design D7; specs/us-station "US Registration Extension" - the kernel seam, wired per
 * the ACTUAL interface: {@code order()} + {@code execute(RegistrationContext)}, nothing
 * invented): persists the optional US extension block 1:1 against the freshly persisted
 * order id, INSIDE the registration transaction (the kernel fires the ordered hook chain
 * after the aggregate write, and ANY exception here propagates - the whole registration
 * rolls back, so no order row can ever survive without a required extension, and no
 * partial ext row can survive a failed order).
 *
 * <p>The ext payload arrives through {@link UsRegistrationExtHolder} (the create path's
 * thread-local stash - the kernel context carries only the aggregate, by design). A
 * registration WITHOUT a stashed ext block (the HIS two-phase confirm path, or a create
 * body without the ext block) persists NO row - the extension table stays 1:1 with the
 * orders that actually carry US fields. The query path reads the row through
 * {@code UsRegistrationExtMapper} directly (the hook seam is the WRITE side; the load
 * side is the us page query).</p>
 *
 * @author birdbro
 */
@Component
public class UsRegistrationHook implements RegistrationHook {

    /** The only hook this service mounts today - order is the contract's formality. */
    private static final int HOOK_ORDER = 0;

    private final UsRegistrationExtMapper usRegistrationExtMapper;

    public UsRegistrationHook(UsRegistrationExtMapper usRegistrationExtMapper) {
        this.usRegistrationExtMapper = usRegistrationExtMapper;
    }

    @Override
    public int order() {
        return HOOK_ORDER;
    }

    @Override
    public void execute(RegistrationContext context) {
        UsRegistrationExt ext = UsRegistrationExtHolder.poll();
        if (ext == null) {
            // no ext block on this registration - no row (the 1:1 table exists only for
            // orders that actually carry US fields)
            return;
        }
        ext.setOrderId(context.getOrderId());
        usRegistrationExtMapper.insert(ext);
    }
}
