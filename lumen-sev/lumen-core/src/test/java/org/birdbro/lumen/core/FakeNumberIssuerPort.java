package org.birdbro.lumen.core;

import java.util.concurrent.atomic.AtomicInteger;

import org.birdbro.lumen.core.number.NumberIssuerPort;
import org.springframework.stereotype.Service;

/**
 * TEST-SOURCES-ONLY deterministic {@link NumberIssuerPort} for the kernel's test host
 * (task 4.1 - the FakePatientRegistryPort precedent): the kernel MAIN sources carry no
 * port implementation (consuming services adapt it to the platform's number-rule
 * service); every kernel test context needs ONE issuer bean for the registration
 * service's constructor injection. Sequential output proves the registration path
 * consumes the port result VERBATIM (stored accession == fake output).
 *
 * @author birdbro
 */
@Service
public class FakeNumberIssuerPort implements NumberIssuerPort {

    /** Static fail-switch: tests flip it to simulate a platform-issuer outage. */
    static boolean FAILING = false;

    /** The most recently issued value (verbatim-consumption assertions read it). */
    static volatile String LAST_ISSUED;

    /** Sequential accession source - the stored number must equal this output. */
    private final AtomicInteger sequence = new AtomicInteger(0);

    @Override
    public String issue(String ruleType) {
        if (FAILING) {
            throw new IllegalStateException("platform issuer unreachable");
        }
        LAST_ISSUED = "AC-FAKE-" + String.format("%06d", sequence.incrementAndGet());
        return LAST_ISSUED;
    }
}
