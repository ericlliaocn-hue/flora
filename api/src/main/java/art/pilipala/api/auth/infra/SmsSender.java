package art.pilipala.api.auth.infra;

import art.pilipala.api.common.ErrorCodes;

/**
 * 短信发送抽象（WP17：smsbao / aliyun / logging）。
 *
 * @throws AuthException {@link ErrorCodes#PROVIDER_UNAVAILABLE} 配置缺失、上游拒绝或网络失败
 */
public interface SmsSender {

    void sendOtp(String phoneE164, String purpose, String code);
}
