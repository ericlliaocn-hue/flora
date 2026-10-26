package art.pilipala.api.auth.application;

import art.pilipala.api.auth.domain.IdentityNormalizer;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.infra.AuthException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

@Service
public class SendOtp {

    private final OtpService otpService;
    private final EmailAuthPolicy emailAuthPolicy;

    public SendOtp(OtpService otpService, EmailAuthPolicy emailAuthPolicy) {
        this.otpService = otpService;
        this.emailAuthPolicy = emailAuthPolicy;
    }

    public OtpService.SendOtpResult send(String channel, String destination, String purposeRaw) {
        OtpPurpose purpose = OtpPurpose.parse(purposeRaw);
        if (purpose == null) {
            throw AuthException.validation();
        }
        if (purpose.requiresLogin() && !StpUtil.isLogin()) {
            throw AuthException.unauthorized();
        }
        String normalized;
        if ("sms".equals(channel)) {
            normalized = IdentityNormalizer.normalizePhone(destination);
        } else if ("email".equals(channel)) {
            emailAuthPolicy.assertEmailOtp(purpose);
            normalized = IdentityNormalizer.normalizeEmail(destination);
        } else {
            throw AuthException.validation();
        }
        if (normalized == null) {
            throw AuthException.validation();
        }
        return otpService.send(channel, normalized, purpose);
    }
}
