package io.github.johneliud.identity_service.service;

public interface EmailService {
    void sendVerificationEmail(String to, String verificationToken);

    void sendPasswordResetEmail(String to, String otpCode);
}
