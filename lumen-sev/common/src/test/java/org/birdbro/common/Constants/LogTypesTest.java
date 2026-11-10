package org.birdbro.common.Constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LogTypes} unit proofs (audit-log-unify-api task 1.3, design D2). PLAIN JUnit,
 * no Spring — zero-infrastructure per the batch plan. Pins the three contracts the
 * single translation point depends on: the five {@code @Log} labels map to their codes,
 * null/blank degrades to {@code null}, and an unknown label degrades to {@code null}
 * (never throws — a future annotation label without a mapping must not break the write
 * path).
 *
 * @author birdbro
 */
class LogTypesTest {

    @Test
    void knownLabelsMapToTheirCodes() {
        assertEquals(1, LogTypes.codeOf("新增"));
        assertEquals(2, LogTypes.codeOf("修改"));
        assertEquals(3, LogTypes.codeOf("删除"));
        assertEquals(4, LogTypes.codeOf("授权"));
        assertEquals(5, LogTypes.codeOf("重置密码"));
    }

    @Test
    void declaredCodeConstantsCarryTheRuledValues() {
        // The dict seed (V25) and the sign codes (Constants.SUCCESS/FAIL/SIGN_OUT,
        // referenced verbatim - NOT re-declared here) together form the ruled vocabulary.
        assertEquals(1, LogTypes.CREATE);
        assertEquals(2, LogTypes.UPDATE);
        assertEquals(3, LogTypes.DELETE);
        assertEquals(4, LogTypes.GRANT);
        assertEquals(5, LogTypes.RESET_PWD);
        assertEquals(10, Constants.SUCCESS);
        assertEquals(11, Constants.FAIL);
        assertEquals(12, Constants.SIGN_OUT);
    }

    @Test
    void nullAndBlankLabelsDegradeToNull() {
        assertNull(LogTypes.codeOf(null));
        assertNull(LogTypes.codeOf(""));
        assertNull(LogTypes.codeOf("   "));
    }

    @Test
    void unknownLabelDegradesToNullNeverThrows() {
        assertNull(LogTypes.codeOf("未知的操作类型"));
        assertNull(LogTypes.codeOf("create")); // English label - not part of the mapping
    }
}
