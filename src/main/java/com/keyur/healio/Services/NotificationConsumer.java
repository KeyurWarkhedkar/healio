package com.keyur.healio.Services;

import com.keyur.healio.Configuration.RabbitConfig;
import com.keyur.healio.DTOs.AppointmentEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationConsumer {

    //private final EmailService emailService;

    @RabbitListener(queues = RabbitConfig.NOTIFY_QUEUE)
    public void consumeNotification(AppointmentEventDto event) {
        log.info("📩 Received event: {}", event);
        //emailService.sendAppointmentEmail(event);
    }
}
