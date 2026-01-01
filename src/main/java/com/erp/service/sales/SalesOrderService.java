package com.erp.service.sales;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Customer;
import com.erp.domain.inventory.Item;
import com.erp.domain.sales.SalesOrder;
import com.erp.domain.sales.SalesOrderItem;
import com.erp.dto.sales.SalesOrderCreateDTO;
import com.erp.dto.sales.SalesOrderItemResponseDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.dto.sales.SalesOrderUpdateDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.CustomerRepository;
import com.erp.repo.inventory.ItemRepository;
import com.erp.repo.sales.SalesOrderRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class SalesOrderService {

    private final SalesOrderRepository repo;
    private final CustomerRepository customerRepo;
    private final ItemRepository itemRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;

    public SalesOrderService(
            SalesOrderRepository repo,
            CustomerRepository customerRepo,
            ItemRepository itemRepo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.itemRepo = itemRepo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
    }

    // --------------------------
    // Create Sales Order (DRAFT)
    // --------------------------
    public SalesOrderResponseDTO create(SalesOrderCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();

        Customer customer = customerRepo.findById(dto.getCustomerId())
                .filter(c -> c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Company company = companyRepo.findById(companyId).orElseThrow();
        User user = userRepo.findById(auth.getCurrentUserId()).orElseThrow();

        BigDecimal total = BigDecimal.ZERO;

        List<SalesOrderItem> items = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            BigDecimal lineTotal = BigDecimal.valueOf(i.getUnitPrice())
                    .multiply(BigDecimal.valueOf(i.getQuantity()));

            return SalesOrderItem.builder()
                    .item(item)
                    .quantity(i.getQuantity())
                    .unitPrice(BigDecimal.valueOf(i.getUnitPrice()))
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        for (SalesOrderItem li : items) {
            total = total.add(li.getLineTotal());
        }

        SalesOrder order = SalesOrder.builder()
                .orderNumber(generateOrderNumber())
                .customer(customer)
                .orderDate(dto.getOrderDate())
                .status("DRAFT")
                .totalAmount(total)
                .company(company)
                .createdByUser(user)
                .items(items)
                .build();

        return toDTO(repo.save(order));
    }

    // --------------------------
    // Confirm Order
    // --------------------------
    public SalesOrderResponseDTO confirm(Long id) {

        SalesOrder order = getEntity(id);

        if (!"DRAFT".equals(order.getStatus())) {
            throw new RuntimeException("Only DRAFT orders can be confirmed");
        }

        order.setStatus("CONFIRMED");
        return toDTO(repo.save(order));
    }

    // --------------------------
    // Get
    // --------------------------
    public SalesOrderResponseDTO get(Long id) {
        return toDTO(getEntity(id));
    }

    // --------------------------
    // List
    // --------------------------
    public List<SalesOrderResponseDTO> list() {
        return repo.findByCompanyId(auth.getCurrentCompanyId())
                .stream().map(this::toDTO).toList();
    }

    // --------------------------
// Update Sales Order (DRAFT only)
// --------------------------
    public SalesOrderResponseDTO update(Long id, SalesOrderUpdateDTO dto) {

        SalesOrder order = getEntity(id);

        if (!"DRAFT".equals(order.getStatus())) {
            throw new RuntimeException("Only DRAFT sales orders can be updated");
        }

        BigDecimal total = BigDecimal.ZERO;

        // Clear existing items (aggregate root rule)
        order.getItems().clear();

        List<SalesOrderItem> updatedItems = dto.getItems().stream().map(i -> {
            Item item = itemRepo.findById(i.getItemId())
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            BigDecimal lineTotal = BigDecimal.valueOf(i.getUnitPrice())
                    .multiply(BigDecimal.valueOf(i.getQuantity()));

            return SalesOrderItem.builder()
                    .item(item)
                    .quantity(i.getQuantity())
                    .unitPrice(BigDecimal.valueOf(i.getUnitPrice()))
                    .lineTotal(lineTotal)
                    .build();
        }).toList();

        for (SalesOrderItem li : updatedItems) {
            total = total.add(li.getLineTotal());
        }

        order.setOrderDate(dto.getOrderDate());
        order.setItems(updatedItems);
        order.setTotalAmount(total);

        return toDTO(repo.save(order));
    }

    // --------------------------
// Cancel Sales Order
// --------------------------
    public SalesOrderResponseDTO cancel(Long id) {

        SalesOrder order = getEntity(id);

        if ("CANCELLED".equals(order.getStatus())) {
            throw new RuntimeException("Sales order is already cancelled");
        }

        order.setStatus("CANCELLED");
        return toDTO(repo.save(order));
    }


    // --------------------------
    // Helpers
    // --------------------------
    private SalesOrder getEntity(Long id) {
        return repo.findById(id)
                .filter(o -> o.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() -> new RuntimeException("Sales order not found or access denied"));
    }

    private String generateOrderNumber() {
        return "SO-" + System.currentTimeMillis();
    }

    private SalesOrderResponseDTO toDTO(SalesOrder so) {
        return SalesOrderResponseDTO.builder()
                .id(so.getId())
                .orderNumber(so.getOrderNumber())
                .customerId(so.getCustomer().getId())
                .orderDate(so.getOrderDate())
                .status(so.getStatus())
                .totalAmount(so.getTotalAmount())
                .items(
                        so.getItems().stream().map(i ->
                                SalesOrderItemResponseDTO.builder()
                                        .itemId(i.getItem().getId())
                                        .quantity(i.getQuantity())
                                        .unitPrice(i.getUnitPrice())
                                        .lineTotal(i.getLineTotal())
                                        .build()
                        ).toList()
                )
                .build();
    }
}
