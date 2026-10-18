package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.Payment;
import com.ordercraft.ordercraft.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Payment addPayment(@RequestBody Payment payment) {
        if (payment != null) payment.setPaymentId(null);
        return paymentService.savePayment(payment);
    }


    @GetMapping
    public List<Payment> getAllPayments() {
        return paymentService.getAllPayments();
    }


    @GetMapping("/{id}")
    public Payment getPayment(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }


    @PutMapping("/{id}")
    public Payment updatePayment(
            @PathVariable Long id,
            @RequestBody Payment payment) {

        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment data is required");
        }

        Payment existingPayment =
                paymentService.getPaymentById(id);

        existingPayment.setInvoiceId(payment.getInvoiceId());
        existingPayment.setPaymentDate(payment.getPaymentDate());
        existingPayment.setAmount(payment.getAmount());
        existingPayment.setMode(payment.getMode());

        return paymentService.savePayment(existingPayment);
    }


    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
    }

}
