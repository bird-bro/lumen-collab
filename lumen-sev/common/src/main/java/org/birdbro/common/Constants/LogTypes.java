package org.birdbro.common.Constants;

import cn.hutool.core.util.StrUtil;

/**
 * 操作日志类型代码 (audit-log-unify-api task 1.3, coordination D2): the Integer code
 * vocabulary at REST in {@code public.log_info.type} — 1~5 operation codes; the sign
 * event codes 10/11/12 are NOT re-declared here, they are the copied
 * {@link Constants#SUCCESS}/{@link Constants#FAIL}/{@link Constants#SIGN_OUT} verbatim
 * (the auth write path stays byte-untouched).
 *
 * <p>The translation String → Integer happens at exactly ONE point —
 * {@code LogInfoServiceImpl.insert} calling {@link #codeOf(String)} AFTER the
 * {@code BeanUtil.copyProperties} copy (b2 probe: hutool 5.8.20's boolean overload
 * EAGERLY converts numeric strings and THROWS on labels — the copy therefore
 * EXCLUDES the type field via {@code CopyOptions.setIgnoreProperties("type")};
 * {@code codeOf} is the only assignment path). Unknown/blank labels degrade to
 * {@code null} — a future {@code @Log} annotation label without a mapping never
 * breaks the write path.</p>
 *
 * @author birdbro
 */
public class LogTypes {

    /**
     * 新增
     */
    public static final int CREATE = 1;

    /**
     * 修改
     */
    public static final int UPDATE = 2;

    /**
     * 删除
     */
    public static final int DELETE = 3;

    /**
     * 授权
     */
    public static final int GRANT = 4;

    /**
     * 重置密码
     */
    public static final int RESET_PWD = 5;

    private LogTypes() {
    }

    /**
     * Maps a {@code @Log} annotation Chinese label to its Integer code; null/blank or
     * an unknown label yield {@code null} — degrade, never throw (design D2: a future
     * annotation label without a mapping lands as a null type, the row still writes).
     *
     * @param label the annotation {@code type} value (新增/修改/删除/授权/重置密码)
     * @return the matching code, or {@code null} when unmapped
     */
    public static Integer codeOf(String label) {
        if (StrUtil.isBlank(label)) {
            return null;
        }
        switch (label) {
            case "新增":
                return CREATE;
            case "修改":
                return UPDATE;
            case "删除":
                return DELETE;
            case "授权":
                return GRANT;
            case "重置密码":
                return RESET_PWD;
            default:
                return null;
        }
    }
}
