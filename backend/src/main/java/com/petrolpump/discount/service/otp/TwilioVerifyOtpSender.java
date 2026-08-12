package com.petrolpump.discount.service.otp;

import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.otp.provider", havingValue = "twilio")
public class TwilioVerifyOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(TwilioVerifyOtpSender.class);

    private final String serviceSid;

    public TwilioVerifyOtpSender(
            @Value("${app.twilio.account-sid}") String accountSid,
            @Value("${app.twilio.auth-token}") String authToken,
            @Value("${app.twilio.verify-service-sid}") String serviceSid) {
        Twilio.init(accountSid, authToken);
        this.serviceSid = serviceSid;
        log.info("OTP provider: Twilio Verify");
    }

    private static String e164(String phone10) {
        return "+91" + phone10;
    }

    @Override
    public void send(String phone10, String otp) {
        // Twilio Verify generates its own code; local otp unused
        Verification.creator(serviceSid, e164(phone10), "sms").create();
    }

    @Override
    public boolean providerVerifies() {
        return true;
    }

    @Override
    public boolean verify(String phone10, String otp) {
        VerificationCheck check = VerificationCheck.creator(serviceSid)
                .setTo(e164(phone10))
                .setCode(otp)
                .create();
        return "approved".equalsIgnoreCase(check.getStatus());
    }
}
