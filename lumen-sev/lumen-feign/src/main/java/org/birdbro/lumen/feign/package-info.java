/**
 * The inter-service contract home (lumen-feign): OpenFeign interfaces + DTOs describing
 * the platform's service-to-service APIs, ADD-ONLY (never rewrite a published
 * contract - add a new method or a new versioned interface). NON-EXECUTABLE shared
 * jar like framework/common/lumen-core: no entry point, no controllers with routes —
 * each declared endpoint is OWNED by the target service; the contract only mirrors
 * its surface. Consumers enable clients with {@code @EnableFeignClients} and point
 * them at direct URLs (no discovery, the 2026-11-03 local-config ruling). The first
 * contract is the platform number-rule service ({@code org.birdbro.lumen.feign.number}).
 *
 * @author birdbro
 */
package org.birdbro.lumen.feign;
