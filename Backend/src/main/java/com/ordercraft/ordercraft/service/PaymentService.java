package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.Invoice;
import com.ordercraft.ordercraft.entity.Payment;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PaymentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository) {

        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    // CREATE / UPDATE PAYMENT
    @Transactional
    public Payment savePayment(Payment payment) {

        validatePayment(payment);

        Long invoiceId = payment.getInvoiceId();
        Long existingPaymentId = payment.getPaymentId();
        Long oldInvoiceId = null;

        // Find the existing payment when updating.
        Payment paymentToSave;

        if (existingPaymentId != null) {

            paymentToSave = paymentRepository
                    .findById(existingPaymentId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Payment not found with id: "
                                    + existingPaymentId
                    ));

            // Remember the previous invoice.
            oldInvoiceId = paymentToSave.getInvoiceId();

            // Update payment fields.
            paymentToSave.setInvoiceId(invoiceId);
            paymentToSave.setPaymentDate(payment.getPaymentDate());
            paymentToSave.setAmount(payment.getAmount());
            paymentToSave.setMode(payment.getMode());

        } else {
            paymentToSave = payment;
        }

        // Check whether the target invoice exists.
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invoice not found with id: " + invoiceId
                ));

        // Validate invoice amount before accepting payment.
        if (!Double.isFinite(invoice.getAmount())
                || invoice.getAmount() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invoice amount must be greater than 0"
            );
        }

        // Calculate existing payments for the target invoice.
        // Exclude the payment being updated.
        double existingPaid = paymentRepository
                .findByInvoiceId(invoiceId)
                .stream()
                .filter(existing ->
                        existingPaymentId == null
                                || !existing.getPaymentId()
                                .equals(existingPaymentId))
                .mapToDouble(Payment::getAmount)
                .sum();

        double newTotal = existingPaid + paymentToSave.getAmount();

        // Prevent overpayment.
        if (!Double.isFinite(newTotal)
                || newTotal > invoice.getAmount()) {

            double remainingBalance = Math.max(
                    0,
                    invoice.getAmount() - existingPaid
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment exceeds the remaining invoice balance. "
                            + "Remaining balance: " + remainingBalance
            );
        }

        // Save the payment.
        Payment savedPayment = paymentRepository.save(paymentToSave);

        // Update the target invoice status.
        updateInvoiceStatus(invoiceId);

        // If the payment moved to another invoice,
        // recalculate the previous invoice status too.
        if (oldInvoiceId != null
                && !oldInvoiceId.equals(invoiceId)) {

            updateInvoiceStatus(oldInvoiceId);
        }

        return savedPayment;
    }

    // PAYMENT VALIDATION
    private void validatePayment(Payment payment) {

        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment data is required"
            );
        }

        if (payment.getInvoiceId() == null
                || payment.getInvoiceId() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid invoice ID is required"
            );
        }

        if (!Double.isFinite(payment.getAmount())
                || payment.getAmount() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment amount must be greater than 0"
            );
        }

        if (payment.getPaymentDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment date is required"
            );
        }

        if (payment.getPaymentDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment date cannot be in the future"
            );
        }

        if (payment.getMode() == null
                || payment.getMode().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment mode is required"
            );
        }

        if (payment.getMode().trim().length() > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment mode cannot exceed 50 characters"
            );
        }

        payment.setMode(payment.getMode().trim());
    }

    // GET ALL PAYMENTS
    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    // GET PAYMENT BY ID
    @Transactional(readOnly = true)
    public Payment getPaymentById(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid payment ID is required"
            );
        }

        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found with id: " + id
                ));
    }

    // DELETE PAYMENT
    @Transactional
    public void deletePayment(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid payment ID is required"
            );
        }

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found with id: " + id
                ));

        Long invoiceId = payment.getInvoiceId();

        paymentRepository.delete(payment);
        paymentRepository.flush();

        // Recalculate invoice status after deletion.
        updateInvoiceStatus(invoiceId);
    }

    // UPDATE INVOICE STATUS BASED ON PAYMENTS
    private void updateInvoiceStatus(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invoice not found with id: " + invoiceId
                ));

        double totalPaid = paymentRepository
                .findByInvoiceId(invoiceId)
                .stream()
                .mapToDouble(Payment::getAmount)
                .sum();

        if (totalPaid <= 0) {
            invoice.setStatus("UNPAID");
        } else if (totalPaid < invoice.getAmount()) {
            invoice.setStatus("PARTIALLY PAID");
        } else {
            invoice.setStatus("PAID");
        }

        invoiceRepository.save(invoice);
    }
}