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
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

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
                    .findByIdForUpdate(existingPaymentId)
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

        // Lock the affected invoice rows in a stable order so concurrent payments cannot
        // both spend the same outstanding balance and status recalculation stays consistent.
        List<Long> invoiceIdsToLock = Stream.of(invoiceId, oldInvoiceId)
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();
        Invoice invoice = null;
        for (Long affectedInvoiceId : invoiceIdsToLock) {
            Invoice lockedInvoice = invoiceRepository.findByIdForUpdate(affectedInvoiceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Invoice not found with id: " + affectedInvoiceId
                    ));
            if (invoiceId.equals(affectedInvoiceId)) {
                invoice = lockedInvoice;
            }
        }

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
        BigDecimal existingPaid = paymentRepository
                .findByInvoiceId(invoiceId)
                .stream()
                .filter(existing ->
                        existingPaymentId == null
                                || !existing.getPaymentId()
                                .equals(existingPaymentId))
                .map(Payment::getAmount)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = existingPaid.add(BigDecimal.valueOf(paymentToSave.getAmount()));
        BigDecimal invoiceAmount = BigDecimal.valueOf(invoice.getAmount());

        // Prevent overpayment.
        if (newTotal.compareTo(invoiceAmount) > 0) {
            BigDecimal remainingBalance = invoiceAmount.subtract(existingPaid).max(BigDecimal.ZERO);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment exceeds the remaining invoice balance. "
                            + "Remaining balance: " + remainingBalance.toPlainString()
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

        if (payment.getPaymentId() != null && payment.getPaymentId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid payment ID is required"
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

        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found with id: " + id
                ));

        Long invoiceId = payment.getInvoiceId();

        invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invoice not found with id: " + invoiceId
                ));

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

        BigDecimal totalPaid = paymentRepository
                .findByInvoiceId(invoiceId)
                .stream()
                .map(Payment::getAmount)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int comparison = totalPaid.compareTo(BigDecimal.ZERO);
        if (comparison <= 0) {
            invoice.setStatus("UNPAID");
        } else if (totalPaid.compareTo(BigDecimal.valueOf(invoice.getAmount())) < 0) {
            invoice.setStatus("PARTIALLY PAID");
        } else {
            invoice.setStatus("PAID");
        }

        invoiceRepository.save(invoice);
    }
}
