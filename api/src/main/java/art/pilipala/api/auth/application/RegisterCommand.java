package art.pilipala.api.auth.application;

public record RegisterCommand(String phone, String email, String password, String otp, String locale) {
}
