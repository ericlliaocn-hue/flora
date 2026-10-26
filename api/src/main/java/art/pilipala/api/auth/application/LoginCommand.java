package art.pilipala.api.auth.application;

public record LoginCommand(String phone, String email, String password, String otp) {
}
