package com.ordercraft.ordercraft.controller;

import com.ordercraft.ordercraft.entity.Invoice;
import com.ordercraft.ordercraft.service.InvoiceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;


    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }


    @PostMapping
    public Invoice addInvoice(@RequestBody Invoice invoice) {
        return invoiceService.saveInvoice(invoice);
    }


    @GetMapping
    public List<Invoice> getAllInvoices() {
        return invoiceService.getAllInvoices();
    }


    @GetMapping("/{id}")
    public Invoice getInvoice(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id);
    }


    @PutMapping("/{id}")
    public Invoice updateInvoice(
            @PathVariable Long id,
            @RequestBody Invoice invoice) {

        Invoice existingInvoice =
                invoiceService.getInvoiceById(id);

        existingInvoice.setOrderId(invoice.getOrderId());
        existingInvoice.setInvoiceDate(invoice.getInvoiceDate());
        existingInvoice.setAmount(invoice.getAmount());
        existingInvoice.setStatus(invoice.getStatus());

        return invoiceService.saveInvoice(existingInvoice);
    }


    @DeleteMapping("/{id}")
    public void deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
    }

}