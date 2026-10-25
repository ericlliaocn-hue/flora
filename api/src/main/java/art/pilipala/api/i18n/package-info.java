/**
 * 友好错误与国际化边界（WP9）。
 *
 * <p>这里落地 {@code i18n_message} 表与「用户偏好 → Accept-Language → zh」的
 * 语言选择链、「DB → 内置种子 → error_code」的缺词条回退链。
 *
 * <p>分层：{@code domain} 是词条记录，{@code infra} 是 SQL，
 * {@code application} 是解析与语言决策。worker 不查这张表、也不拼用户语言文案
 * （见 ADR 0004 / ADR 0005）。
 */
package art.pilipala.api.i18n;
