package org.birdbro.lumen.core.entity;

/**
 * The report's pluggable completion-source seam (patient-domain decision #9: report
 * completion "来源可插拔" is a v1 kernel-reserved seam). LOCAL = the local writing flow;
 * REMOTE = the remote-return flow (lumen-remote v2 lands reports directly usable). The
 * kernel carries the field; remote integration is out of scope here.
 *
 * @author birdbro
 */
public enum ReportCompletionSource {
    /** 本地书写流 (default). */
    LOCAL,
    /** 远程回传流 (lumen-remote v2). */
    REMOTE
}
