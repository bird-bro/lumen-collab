package org.birdbro.framework.utils;

import javax.servlet.http.HttpServletRequest;

import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.OperatingSystem;
import eu.bitwalker.useragentutils.UserAgent;

/**
 * Null-safe User-Agent parsing over the copied bitwalker lineage (lumen adaptation (10),
 * user decision 2026-11-04): API-first clients (scripts, service-to-service calls,
 * probes) send NO User-Agent or an UNVERSIONED one - the copied call sites chained
 * {@code getBrowserVersion().getVersion()} and NPE'd, silently dropping sign rows from
 * the async pool and breaking {@code @Log} endpoints on the synchronous path.
 *
 * @author birdbro
 */
public final class UserAgents {

    private UserAgents() {
    }

    /**
     * Parses the header without ever throwing: a missing or blank UA yields the
     * {@code UNKNOWN} agent with no browser version (the engine column stays null).
     */
    public static UserAgent fromHeader(String userAgentHeader) {
        if (userAgentHeader == null || userAgentHeader.isBlank()) {
            return new UserAgent(OperatingSystem.UNKNOWN, Browser.UNKNOWN);
        }
        return UserAgent.parseUserAgentString(userAgentHeader);
    }

    /** Engine version or null (the {@code UNKNOWN} browser carries no Version). */
    public static String engine(UserAgent userAgent) {
        return userAgent == null || userAgent.getBrowserVersion() == null
                ? null
                : userAgent.getBrowserVersion().getVersion();
    }
}
