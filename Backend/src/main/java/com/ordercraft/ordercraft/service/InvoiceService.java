package com.ordercraft.ordercraft.service;

import com.ordercraft.ordercraft.entity.Invoice;
import com.ordercraft.ordercraft.entity.Payment;
import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PaymentRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PaymentRepository paymentRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            SalesOrderRepository salesOrderRepository,
            PaymentRepository paymentRepository) {

        this.invoiceRepository = invoiceRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.paymentRepository = paymentRepository;
    }

    // CREATE / UPDATE INVOICE
    @Transactional
    public Invoice saveInvoice(Invoice invoice) {

        if (invoice == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invoice data is required"
            );
        }

        Long orderId = invoice.getOrderId();

        if (orderId == null || orderId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid order ID is required"
            );
        }

        Long invoiceId = invoice.getInvoiceId();
        if (invoiceId != null && invoiceId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid invoice ID is required");
        }

        SalesOrder salesOrder = salesOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sales order not found with id: " + orderId
                ));

        boolean anotherInvoiceExists = invoiceId == null
                ? invoiceRepository.existsByOrderId(orderId)
                : invoiceRepository.existsByOrderIdAndInvoiceIdNot(orderId, invoiceId);
        if (anotherInvoiceExists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A sales order can have only one invoice"
            );
        }

        if (invoice.getInvoiceDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invoice date is required"
            );
        }

        if (!Double.isFinite(invoice.getAmount())
                || invoice.getAmount() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invoice amount must be a finite value greater than 0"
            );
        }

        if (!Double.isFinite(salesOrder.getTotalAmount())
                || salesOrder.getTotalAmount() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The sales order must have a positive total before it can be invoiced"
            );
        }

        if (!sameCurrencyAmount(invoice.getAmount(), salesOrder.getTotalAmount())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invoice amount must match the sales order total of "
                            + salesOrder.getTotalAmount()
            );
        }

        // Do not allow an invoice amount below payments already received.
        if (invoiceId != null) {

            Invoice existingInvoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Invoice not found with id: " + invoiceId
                    ));

            List<Payment> existingPayments =
                    paymentRepository.findByInvoiceId(invoiceId);

            BigDecimal totalPaid = existingPayments.stream()
                    .map(Payment::getAmount)
                    .map(BigDecimal::valueOf)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (BigDecimal.valueOf(invoice.getAmount()).compareTo(totalPaid) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invoice amount cannot be less than payments already received. "
                                + "Total paid: " + totalPaid.toPlainString()
                );
            }

            // Keep the invoice's current identity.
            invoice.setInvoiceId(existingInvoice.getInvoiceId());
        }

        // Payment state is always derived from persisted payments.
        if (invoiceId == null) {
            invoice.setStatus("UNPAID");
        } else {
            updateInvoiceStatusForAmount(invoice);
        }

        return invoiceRepository.save(invoice);
    }

    // GET ALL INVOICES
    @Transactional(readOnly = true)
    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    // GET INVOICE BY ID
    @Transactional(readOnly = true)
    public Invoice getInvoiceById(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid invoice ID is required"
            );
        }

        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invoice not found with id: " + id
                ));
    }

    // DELETE INVOICE
    @Transactional
    public void deleteInvoice(Long id) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid invoice ID is required"
            );
        }

        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invoice not found with id: " + id
                ));

        List<Payment> linkedPayments =
                paymentRepository.findByInvoiceId(id);

        if (!linkedPayments.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete invoice because payments are linked to it. "
                            + "Delete or reassign those payments first."
            );
        }

        invoiceRepository.delete(invoice);
    }

    // SET STATUS BASED ON PAYMENTS FOR THE UPDATED AMOUNT
    private void updateInvoiceStatusForAmount(Invoice invoice) {

        BigDecimal totalPaid = paymentRepository
                .findByInvoiceId(invoice.getInvoiceId())
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
    }

    private boolean sameCurrencyAmount(double first, double second) {
        return Math.abs(BigDecimal.valueOf(first)
                .subtract(BigDecimal.valueOf(second))
                .doubleValue()) < 0.005;
    }
}
