package org.birdbro.lumen.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.birdbro.lumen.setting.mapper.DictModalityMapper;

/**
 * Locks the modality-validation anchor at the catalog ID (dict-lookup-by-id ruling
 * 2026-11-19; change {@code modality-dict-id-anchor-sev}): {@code countLiveModality}
 * resolves the shared modality vocabulary through catalog 1024's deterministic id -
 * the SQL text carries NO catalog name, so an operator rename can never blind the
 * check.
 *
 * <p><b>RED history:</b> with the former {@code c.name = '检查类型'} anchor this
 * suite fails against a spec-true database - the retired catalog stays absent
 * ({@code AuthDeviceRoomSeedTests} guards exactly that; the old test-fixture guard
 * used to paper over the gap by self-creating a spec-false 1027 catalog), so every
 * token resolved to 0. GREEN: the id anchor counts the V27-seeded 1024 rows.</p>
 *
 * @author birdbro
 */
class DictModalityMapperTests extends SettingIntegrationTestBase {

    @Autowired
    private DictModalityMapper dictModalityMapper;

    /** Live seeded tokens count 1 through the id anchor (1024 is the single source). */
    @Test
    void liveTokenResolvesThroughCatalogId() {
        assertEquals(1, dictModalityMapper.countLiveModality("CT"),
                "the seeded CT row must be the single live match under catalog 1024");
        assertEquals(1, dictModalityMapper.countLiveModality("US"),
                "the seeded US row must be the single live match under catalog 1024");
    }

    /** Unknown tokens stay rejected - and the anchor carries no name dependency. */
    @Test
    void unknownTokenStaysRejected() {
        assertEquals(0, dictModalityMapper.countLiveModality("NO_SUCH_TOKEN"),
                "a token absent from catalog 1024 must never validate");
    }
}
