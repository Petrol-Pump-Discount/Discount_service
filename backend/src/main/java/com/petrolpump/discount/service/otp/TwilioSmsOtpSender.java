package com.petrolpump.discount.service.otp;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sends OTP via Twilio Programmable Messaging so the SMS body is fully customizable.
 * (Twilio Verify uses a fixed “verification code” template and trial “SAMPLE TEST” wording.)
 */
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "twilio")
public class TwilioSmsOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(TwilioSmsOtpSender.class);

    private final String fromNumber;
    private final String bodyTemplate;

    public TwilioSmsOtpSender(
            @Value("${app.twilio.account-sid}") String accountSid,
            @Value("${app.twilio.auth-token}") String authToken,
            @Value("${app.twilio.from-number}") String fromNumber,
            @Value("${app.otp.sms-template:Your OTP for Nagashree Service Station Login is: {otp}}") String bodyTemplate) {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            throw new IllegalStateException("TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN are required");
        }
        if (fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException("TWILIO_FROM_NUMBER is required (E.164, e.g. +1… or +91…)");
        }
        Twilio.init(accountSid, authToken);
        this.fromNumber = fromNumber.trim();
        this.bodyTemplate = bodyTemplate == null || bodyTemplate.isBlank()
                ? "Your OTP for Nagashree Service Station Login is: {otp}"
                : bodyTemplate;
        log.info("OTP provider: Twilio SMS from {}", this.fromNumber);
    }

    private static String e164(String phone10) {
        return "+91" + phone10;
    }

    @Override
    public void send(String phone10, String otp) {
        String body = bodyTemplate.replace("{otp}", otp);
        Message.creator(new PhoneNumber(e164(phone10)), new PhoneNumber(fromNumber), body).create();
    }

    @Override
    public boolean providerVerifies() {
        return false; // app verifies the OTP we generated
    }
}
