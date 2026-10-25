/**
 * 预付费钱包与单账本（WP6）。
 *
 * <p>账本只有一套：额度冻结、确认与释放只落在 pilipala 业务库的
 * {@code wallet} + {@code wallet_hold} + {@code wallet_ledger}。
 * 模型网关（Anytoken）是网关不是第二套用户 / 配额账本，禁止在这里做双账本。
 *
 * <p>整个包只在 api 进程里；worker 不依赖也不触碰这些表（ADR 0004）。
 */
package art.pilipala.api.billing;
