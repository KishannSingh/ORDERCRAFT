package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.entity.Invoice;
import com.ordercraft.ordercraft.entity.Payment;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PaymentRepository;
import com.ordercraft.ordercraft.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock InvoiceRepository invoiceRepository;

    @InjectMocks PaymentService service;

    @Test
    void partialPaymentLeavesInvoicePartiallyPaid() {
        Invoice invoice = invoice(100.00);
        Payment incoming = payment(25.00);
        when(invoiceRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findById(5L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(5L)).thenReturn(List.of(), List.of(incoming));
        when(paymentRepository.save(incoming)).thenReturn(incoming);
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        service.savePayment(incoming);

        assertEquals("PARTIALLY PAID", invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void acceptsPaymentUpToBalanceAndMarksInvoicePaid() {
        Invoice invoice = invoice(100.00);
        Payment prior = payment(25.00);
        Payment incoming = payment(75.00);
        when(invoiceRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findById(5L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(5L))
                .thenReturn(List.of(prior), List.of(prior, incoming));
        when(paymentRepository.save(incoming)).thenReturn(incoming);
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        Payment saved = service.savePayment(incoming);

        assertEquals(incoming, saved);
        assertEquals("PAID", invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void rejectsPaymentAboveOutstandingBalance() {
        Invoice invoice = invoice(100.00);
        Payment prior = payment(80.00);
        Payment incoming = payment(20.01);
        when(invoiceRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(5L)).thenReturn(List.of(prior));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.savePayment(incoming)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(paymentRepository, never()).save(incoming);
    }

    @Test
    void deletingFinalPaymentReturnsInvoiceToUnpaid() {
        Invoice invoice = invoice(100.00);
        Payment existing = payment(25.00);
        existing.setPaymentId(9L);
        when(paymentRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(existing));
        when(invoiceRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findById(5L)).thenReturn(Optional.of(invoice));
        when(paymentRepository.findByInvoiceId(5L)).thenReturn(List.of());
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        service.deletePayment(9L);

        assertEquals("UNPAID", invoice.getStatus());
        verify(paymentRepository).delete(existing);
        verify(invoiceRepository).save(invoice);
    }

    private Invoice invoice(double amount) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(5L);
        invoice.setAmount(amount);
        return invoice;
    }

    private Payment payment(double amount) {
        Payment payment = new Payment();
        payment.setInvoiceId(5L);
        payment.setPaymentDate(LocalDate.now());
        payment.setAmount(amount);
        payment.setMode("UPI");
        return payment;
    }
}
