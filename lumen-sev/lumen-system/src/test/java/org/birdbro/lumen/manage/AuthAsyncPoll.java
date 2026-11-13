package org.birdbro.lumen.manage;

import java.util.function.BooleanSupplier;

/**
 * Polls the shared test database for an ASYNCHRONOUSLY written row (system-data batch 2).
 * The copied log machinery lands rows on a {@code AsyncManager} scheduled task 10 ms after
 * the request thread returns - a single sleep would be a flaky race, so every async
 * assertion polls the database until the row appears or the window closes.
 *
 * @author birdbro
 */
final class AuthAsyncPoll {

    /** Poll rounds and interval: 20 x 250 ms = a 5 s window per async row. */
    private static final int ROUNDS = 20;
    private static final long INTERVAL_MS = 250;

    private AuthAsyncPoll() {
    }

    /**
     * Polls until {@code condition} holds, then returns; throws a descriptive
     * {@link AssertionError} when the window closes without the row appearing.
     */
    static void until(BooleanSupplier condition, String description) {
        for (int round = 0; round < ROUNDS && !condition.getAsBoolean(); round++) {
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("async write did not appear within the "
                    + (ROUNDS * INTERVAL_MS) + " ms poll window: " + description);
        }
    }
}
