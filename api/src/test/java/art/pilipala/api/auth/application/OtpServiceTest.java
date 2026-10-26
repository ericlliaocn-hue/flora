package art.pilipala.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.EmailSender;
import art.pilipala.api.auth.infra.OtpRedisStore;
import art.pilipala.api.auth.infra.SmsSender;
import art.pilipala.api.common.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final String PHONE = "+8617277889776";

    @Mock
    private OtpRedisStore store;
    @Mock
    private SmsSender smsSender;
    @Mock
    private EmailSender emailSender;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(store, smsSender, emailSender);
    }

    @Test
    void destCooldownBlocksLoginAfterRegisterSend() {
        when(store.onCooldown(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> otpService.send("sms", PHONE, OtpPurpose.LOGIN))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).errorCode()).isEqualTo(ErrorCodes.OTP_RATE_LIMITED));
        verify(smsSender, never()).sendOtp(anyString(), anyString(), anyString());
        verify(store, never()).saveCode(anyString(), anyString(), anyString());
    }

    @Test
    void sendMarksCooldownByDestinationOnly() {
        when(store.onCooldown(PHONE)).thenReturn(false);
        when(store.isOverHourlyLimit(PHONE)).thenReturn(false);
        when(store.isOverDailyLimit(PHONE)).thenReturn(false);

        otpService.send("sms", PHONE, OtpPurpose.REGISTER);

        verify(store).markCooldown(PHONE);
        verify(smsSender).sendOtp(org.mockito.ArgumentMatchers.eq(PHONE), org.mockito.ArgumentMatchers.eq("register"), anyString());
    }

    @Test
    void registerVerifyAcceptsUnconsumedLoginCode() {
        when(store.matches("register", PHONE, "917354")).thenReturn(false);
        when(store.matches("login", PHONE, "917354")).thenReturn(true);

        assertThat(otpService.verifyAndConsume(OtpPurpose.REGISTER, PHONE, "917354")).isTrue();
        verify(store).consume("login", PHONE);
        verify(store, never()).verifyAndConsume(anyString(), anyString(), anyString());
    }

    @Test
    void loginVerifyAcceptsUnconsumedRegisterCode() {
        when(store.matches("login", PHONE, "289500")).thenReturn(false);
        when(store.matches("register", PHONE, "289500")).thenReturn(true);

        assertThat(otpService.verifyAndConsume(OtpPurpose.LOGIN, PHONE, "289500")).isTrue();
        verify(store).consume("register", PHONE);
        verify(store, never()).verifyAndConsume(anyString(), anyString(), anyString());
    }

    @Test
    void primaryPurposeStillWinsWhenBothExist() {
        when(store.matches("register", PHONE, "289500")).thenReturn(true);

        assertThat(otpService.verifyAndConsume(OtpPurpose.REGISTER, PHONE, "289500")).isTrue();
        verify(store).consume("register", PHONE);
        verify(store, never()).matches("login", PHONE, "289500");
    }

    @Test
    void resetPasswordDoesNotFallBackToLoginOrRegister() {
        when(store.matches("reset_password", PHONE, "917354")).thenReturn(false);
        when(store.verifyAndConsume("reset_password", PHONE, "917354")).thenReturn(false);

        assertThat(otpService.verifyAndConsume(OtpPurpose.RESET_PASSWORD, PHONE, "917354")).isFalse();
        verify(store, never()).matches("login", PHONE, "917354");
        verify(store, never()).matches("register", PHONE, "917354");
        verify(store).verifyAndConsume("reset_password", PHONE, "917354");
    }

    @Test
    void bothMissBumpsOnlyThePrimaryPurpose() {
        when(store.matches("register", PHONE, "000000")).thenReturn(false);
        when(store.matches("login", PHONE, "000000")).thenReturn(false);
        when(store.verifyAndConsume("register", PHONE, "000000")).thenReturn(false);

        assertThat(otpService.verifyAndConsume(OtpPurpose.REGISTER, PHONE, "000000")).isFalse();
        verify(store).verifyAndConsume("register", PHONE, "000000");
        verify(store, never()).verifyAndConsume("login", PHONE, "000000");
        verify(store, never()).consume(anyString(), anyString());
    }
}
