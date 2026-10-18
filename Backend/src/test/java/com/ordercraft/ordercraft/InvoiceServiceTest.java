package com.ordercraft.ordercraft;

import com.ordercraft.ordercraft.entity.Invoice;
import com.ordercraft.ordercraft.entity.SalesOrder;
import com.ordercraft.ordercraft.repository.InvoiceRepository;
import com.ordercraft.ordercraft.repository.PaymentRepository;
import com.ordercraft.ordercraft.repository.SalesOrderRepository;
import com.ordercraft.ordercraft.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock SalesOrderRepository salesOrderRepository;
    @Mock PaymentRepository paymentRepository;

    @InjectMocks InvoiceService service;

    @Test
    void invoiceMustMatchSalesOrderTotal() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(2L);
        order.setTotalAmount(150.00);
        when(salesOrderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(order));

        Invoice invoice = invoice(149.99);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveInvoice(invoice)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void invoiceStartsUnpaidWhenAmountMatchesOrder() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(2L);
        order.setTotalAmount(150.00);
        when(salesOrderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(order));
        when(invoiceRepository.save(org.mockito.ArgumentMatchers.any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = service.saveInvoice(invoice(150.00));

        assertEquals("UNPAID", invoice.getStatus());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    void rejectsASecondInvoiceForTheSameSalesOrder() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(2L);
        order.setTotalAmount(150.00);
        when(salesOrderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(order));
        when(invoiceRepository.existsByOrderId(2L)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveInvoice(invoice(150.00))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(invoiceRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsNotFoundWhenInvoiceReferencesAMissingSalesOrder() {
        when(salesOrderRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        Invoice invoice = invoice(150.00);
        invoice.setOrderId(404L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.saveInvoice(invoice)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(invoiceRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Invoice invoice(double amount) {
        Invoice invoice = new Invoice();
        invoice.setOrderId(2L);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setAmount(amount);
        invoice.setStatus("CLIENT STATUS IS IGNORED");
        return invoice;
    }
}
