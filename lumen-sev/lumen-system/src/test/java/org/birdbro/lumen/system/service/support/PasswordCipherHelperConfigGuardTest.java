package org.birdbro.lumen.system.service.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config fail-fast slice proof (password-transport-encryption-api task 1.4, design T5 /
 * D2): a service whose private key is absent, blank, or malformed must NOT boot into a
 * half-working state - every password endpoint would 500/A0400 unpredictably. The
 * helper's constructor is the guard: an {@link IllegalStateException} with a pointed
 * message fails the WHOLE context startup (ApplicationContextRunner slice - no server,
 * no datasource, just the bean).
 *
 * @author birdbro
 */
class PasswordCipherHelperConfigGuardTest {

    private static final String KEY_ID_PROPERTY = "lumen.security.password-crypto.key-id";
    private static final String D_PROPERTY = "lumen.security.password-crypto.sm2-private-key";
    private static final String TEST_D =
            "56d66802243a734aba690c6f2553ca69c87e8e78f9b282a19686d7fe1683cd83";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PasswordCipherHelper.class);

    @Test
    void missingPropertiesFailStartupWithPointedMessage() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            // The FIRST missing property (key-id) names the password-crypto namespace;
            // the blank-key case below pins the sm2-private-key wording.
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("password-crypto");
        });
    }

    @Test
    void blankPrivateKeyFailsStartup() {
        runner.withPropertyValues(KEY_ID_PROPERTY + "=sm2-test", D_PROPERTY + "=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasStackTraceContaining("sm2-private-key");
        });
    }

    @Test
    void shortPrivateKeyFailsStartup() {
        runner.withPropertyValues(KEY_ID_PROPERTY + "=sm2-test", D_PROPERTY + "=abcd1234")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("32-byte");
                });
    }

    @Test
    void nonHexPrivateKeyFailsStartup() {
        runner.withPropertyValues(KEY_ID_PROPERTY + "=sm2-test",
                D_PROPERTY + "=zzzz" + TEST_D.substring(4)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void validPropertiesBootTheHelper() {
        runner.withPropertyValues(KEY_ID_PROPERTY + "=sm2-test", D_PROPERTY + "=" + TEST_D)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PasswordCipherHelper.class)).isNotNull();
                });
    }
}
