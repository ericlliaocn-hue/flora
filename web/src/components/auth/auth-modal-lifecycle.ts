/**
 * 认证弹层只在「关闭 → 打开」时整表重置；登录 / 注册 / 忘记密码互切时保留手机号和倒计时。
 */
export function shouldResetAuthModal(open: boolean, wasOpen: boolean): boolean {
    return open && !wasOpen;
}
