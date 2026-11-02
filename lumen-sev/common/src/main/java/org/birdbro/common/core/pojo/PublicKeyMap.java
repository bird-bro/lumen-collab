package org.birdbro.common.core.pojo;

import lombok.Data;

/**
 * @author birdbro
 */
@Data
public class PublicKeyMap {
    private String ownModulus;
    private String exponent;

    @Override
    public String toString() {
        return "PublicKeyMap [modulus=" + ownModulus + ", exponent=" + exponent + "]";
    }
}
