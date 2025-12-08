package com.keyur.healio.Repositories;

import com.keyur.healio.Entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    @Query("select p from Payment p where p.paymentStatus = com.keyur.healio.Enums.PaymentStatus.PENDING and p.appointment.id = :id")
    public Optional<Payment> findPendingPaymentByAppointmentId(@Param("id") int appointmentId);

    public Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
}
