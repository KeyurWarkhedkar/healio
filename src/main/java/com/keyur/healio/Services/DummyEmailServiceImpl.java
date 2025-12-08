package com.keyur.healio.Services;

import org.springframework.stereotype.Service;

@Service
public class DummyEmailServiceImpl implements EmailService {
    @Override
    public void sendEmail(String to, String subject, String body) {
        System.out.println("==== Sending email ====");
        System.out.println("To: " + to);
        System.out.println("Subject: " + subject);
        System.out.println("Body: " + body);
        System.out.println("=====================");
    }
}
