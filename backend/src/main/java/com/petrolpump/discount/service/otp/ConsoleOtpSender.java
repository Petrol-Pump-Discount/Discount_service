package com.petrolpump.discount.service.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local/dev SMS stand-in: logs OTP to server logs only (never returned in API).
 * Activate with app.otp.provider=console
 */
@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpSender.class);

    @Override
    public void send(String phone10, String otp) {
        log.info("OTP for +91{} => {} (console provider — check server logs)", phone10, otp);
    }
}
