package com.petrolpump.discount.service.otp;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends OTP via Twilio Programmable Messaging so the SMS body is fully customizable.
 */
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "twilio")
public class TwilioSmsOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(TwilioSmsOtpSender.class);
    private static final String DEFAULT_BODY = "Your OTP for Nagashree Service Station Login is: {otp}";
    private static final long SEND_TIMEOUT_SEC = 12;

    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String bodyTemplate;
    private final ExecutorService sendPool = Executors.newFixedThreadPool(2);
    private volatile boolean initialized;

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
        // Use 422 (not 502): Cloudflare replaces origin 502 bodies with "error code: 502",
        // which made the app show a misleading "busy reading bills" message on Sign in.
        if (accountSid.isBlank() || authToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SMS is not configured. Please try again later.");
        }
        if (fromNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SMS is not configured. Please try again later.");
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
        Future<Message> fut = sendPool.submit(() ->
                Message.creator(new PhoneNumber(e164(phone10)), new PhoneNumber(fromNumber), body).create());
        try {
            fut.get(SEND_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            fut.cancel(true);
            log.error("Twilio OTP timed out after {}s for {}", SEND_TIMEOUT_SEC, phone10);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not send OTP right now. Wait a moment and try again.");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.error("Twilio OTP failed for {}: {}", phone10, cause.toString());
            if (cause instanceof ApiException) {
                ApiException api = (ApiException) cause;
                Integer codeObj = api.getCode();
                int code = codeObj == null ? 0 : codeObj;
                // 20003 auth, 21211 invalid to, 21608 trial can't SMS unverified, 21614 invalid mobile
                if (code == 21608 || code == 21614 || code == 21211) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Cannot send OTP to this number. Check the mobile number or Twilio trial limits.");
                }
                if (code == 20003) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "SMS service authentication failed. Contact the station admin.");
                }
            }
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not send OTP right now. Wait a moment and try again.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            fut.cancel(true);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OTP send interrupted");
        }
    }

    @Override
    public boolean providerVerifies() {
        return false;
    }
}
