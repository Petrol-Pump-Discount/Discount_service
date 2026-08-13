package com.petrolpump.discount.service.otp;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends OTP via Twilio Programmable Messaging so the SMS body is fully customizable.
 */
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "twilio")
public class TwilioSmsOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(TwilioSmsOtpSender.class);
    private static final String DEFAULT_BODY = "Your OTP for Nagashree Service Station Login is: {otp}";

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String bodyTemplate;
    private boolean initialized;

    public TwilioSmsOtpSender(
            @Value("${app.twilio.account-sid:}") String accountSid,
            @Value("${app.twilio.auth-token:}") String authToken,
            @Value("${app.twilio.from-number:}") String fromNumber,
            @Value("${app.otp.sms-template:}") String bodyTemplate) {
        this.accountSid = accountSid == null ? "" : accountSid.trim();
        this.authToken = authToken == null ? "" : authToken.trim();
        this.fromNumber = fromNumber == null ? "" : fromNumber.trim();
        this.bodyTemplate = bodyTemplate == null || bodyTemplate.isBlank() ? DEFAULT_BODY : bodyTemplate;
        if (this.fromNumber.isBlank()) {
            log.warn("TWILIO_FROM_NUMBER is empty — app will start but OTP SMS will fail until set");
        } else {
            log.info("OTP provider: Twilio SMS from {}", this.fromNumber);
        }
    }

    private void ensureInit() {
        if (initialized) return;
        if (accountSid.isBlank() || authToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Twilio account not configured");
        }
        if (fromNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "TWILIO_FROM_NUMBER missing — add your Twilio phone number in .env");
        }
        Twilio.init(accountSid, authToken);
        initialized = true;
    }

    private static String e164(String phone10) {
        return "+91" + phone10;
    }

    @Override
    public void send(String phone10, String otp) {
        ensureInit();
        String body = bodyTemplate.replace("{otp}", otp);
        Message.creator(new PhoneNumber(e164(phone10)), new PhoneNumber(fromNumber), body).create();
    }

    @Override
    public boolean providerVerifies() {
        return false;
    }
}
