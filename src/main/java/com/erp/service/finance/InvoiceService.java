package com.erp.service.finance;

import com.erp.domain.finance.Invoices;
import com.erp.dto.finance.InvoiceRequest;
import com.erp.dto.finance.InvoiceResponse;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.domain.inventory.Customer;
import com.erp.domain.inventory.Orders;
import com.erp.repo.inventory.CustomerRepository;
import com.erp.repo.inventory.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, CustomerRepository customerRepository, OrderRepository orderRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
    }

    public InvoiceResponse createInvoice(InvoiceRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        Orders order = orderRepository.findById(request.getOrderId()).orElse(null);

        Invoices invoice = new Invoices();
        invoice.setInvoiceId(request.getInvoiceId());
        invoice.setCustomer(customer);
        invoice.setOrder(order);
        invoice.setAmount(request.getAmount());
        invoice.setOpenAmount(request.getOpenAmount());
        invoice.setOutstanding(request.getOutstanding());
        invoice.setInterestRate(request.getInterestRate());
        invoice.setDueDate(request.getDueDate());
        invoice.setStatus(request.getStatus());
        invoice.setNotesRemarks(request.getNotesRemarks());
        invoice.setCreatedAt(Instant.now());

        Invoices saved = invoiceRepository.save(invoice);
        return mapToResponse(saved);
    }

    public List<InvoiceResponse> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public InvoiceResponse getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
    }

    public List<InvoiceResponse> getInvoicesByCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        return invoiceRepository.findByCustomer(customer)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<InvoiceResponse> getInvoicesByStatus(String status) {
        return invoiceRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public InvoiceResponse updateInvoice(Long id, InvoiceRequest request) {
        Invoices invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        invoice.setStatus(request.getStatus());
        invoice.setOpenAmount(request.getOpenAmount());
        invoice.setOutstanding(request.getOutstanding());
        invoice.setNotesRemarks(request.getNotesRemarks());
        invoice.setPaidDate(Instant.now());

        return mapToResponse(invoiceRepository.save(invoice));
    }

    public void deleteInvoice(Long id) {
        invoiceRepository.deleteById(id);
    }

    private InvoiceResponse mapToResponse(Invoices invoice) {
        InvoiceResponse resp = new InvoiceResponse();
        resp.setId(invoice.getId());
        resp.setInvoiceId(invoice.getInvoiceId());
        resp.setCustomerName(invoice.getCustomer() != null ? invoice.getCustomer().getName() : null);
        resp.setOrderName(invoice.getOrder() != null ? invoice.getOrder().getOrderName() : null);
        resp.setStatus(invoice.getStatus());
        resp.setAmount(invoice.getAmount());
        resp.setOutstanding(invoice.getOutstanding());
        resp.setDueDate(invoice.getDueDate());
        resp.setPaidDate(invoice.getPaidDate());
        resp.setNotesRemarks(invoice.getNotesRemarks());
        return resp;
    }
}
