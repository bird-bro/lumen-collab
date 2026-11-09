/// <reference types="vite/client" />

/**
 * sm-crypto ships no type declarations (0.5.8) - the surface password-transport-
 * encryption uses is exactly these two calls (coordination password-transport-
 * encryption D4). Kept minimal: no other module may reach into the library.
 */
declare module 'sm-crypto' {
  export const sm2: {
    /**
     * C1C3C2 encrypt; cipherMode 1. The native output is hex with a MARKERLESS C1 -
     * the wire format prepends "04" (BC's engine on the backend requires the marker).
     */
    doEncrypt(msg: string, publicKey: string, cipherMode: 0 | 1): string
    /** C1C3C2 decrypt of the MARKERLESS hex form; cipherMode 1. */
    doDecrypt(encryptData: string, privateKey: string, cipherMode: 0 | 1): string
  }
}
