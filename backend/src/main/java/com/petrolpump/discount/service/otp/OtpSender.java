package com.petrolpump.discount.service.otp;

public interface OtpSender {
    /** Send OTP to 10-digit Indian mobile. */
    void send(String phone10, String otp);

    /** true when OTP is delivered by the provider (e.g. Twilio Verify) and not checked locally. */
    default boolean providerVerifies() {
        return false;
    }

    /** Provider-side verify when {@link #providerVerifies()} is true. */
    default boolean verify(String phone10, String otp) {
        throw new UnsupportedOperationException("Local verify only");
    }
}
