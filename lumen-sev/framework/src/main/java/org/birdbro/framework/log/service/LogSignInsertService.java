package org.birdbro.framework.log.service;

import org.birdbro.common.core.pojo.LogSign;

/**
 * Sign-event write service (audit-log-unify-api b4; renamed from the deleted
 * {@code ILogSignService} whose query half died with the dropped
 * {@code public.log_sign} family — V25): the single method translates the
 * {@link LogSign} transport POJO into a {@code public.log_info} audit row
 * (title = msg, type Integer passthrough, token anchor → the {@code session}
 * column). One interface, one consumer ({@code AsyncFactory.recordForSign}).
 *
 * @author birdbro
 * @since 2026-11-10
 */
public interface LogSignInsertService {

    /**
     * Translates and inserts one sign event into {@code public.log_info}.
     *
     * @param sign the sign-event transport (msg/type/uid/account/token anchor/UA fields)
     * @return {@code true} when the row landed
     */
    boolean insert(LogSign sign);
}
