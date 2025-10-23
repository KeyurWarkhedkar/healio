package com.keyur.healio.Services;

import com.keyur.healio.Configuration.RabbitConfig;
import com.keyur.healio.DTOs.AppointmentEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppointmentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishBooked(AppointmentEventDto event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY_BOOKED,
                event
        );
    }

    public void publishCancelled(AppointmentEventDto event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY_CANCELLED,
                event
        );
    }
}
