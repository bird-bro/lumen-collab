package org.birdbro.lumen.manage.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Password-crypto key distribution body ({@code GET /api/v1/system/auth-key},
 * password-transport-encryption-api task 2.3, coordination password-transport-encryption
 * D3): the configured key id plus the DERIVED SM2 public point - the frontend feeds
 * {@code publicKey} to {@code sm2.doEncrypt} verbatim (after prepending the 04 marker to
 * its own markerless output). The copied {@code GlobalResponseAdvice} wraps this into the
 * standard {@code code:"200"} envelope. The PRIVATE key never leaves the server.
 *
 * @author birdbro
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthKeyResponse {

    @ApiModelProperty("Active SM2 key id (rotation hook; the configured key-id)")
    private String keyId;

    @ApiModelProperty("Derived SM2 public key, 04-prefixed 130-char hex uncompressed point")
    private String publicKey;
}
