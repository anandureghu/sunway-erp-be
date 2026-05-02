package com.erp.service.inventory;

import com.erp.domain.inventory.Orders;
import com.erp.domain.inventory.Vendor;
import com.erp.dto.inventory.OrderRequest;
import com.erp.repo.inventory.OrderRepository;
import com.erp.repo.inventory.VendorRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final VendorRepository vendorRepository;
    private final AuthContext authContext;

    public OrderService(
            OrderRepository orderRepository,
            VendorRepository vendorRepository,
            AuthContext authContext) {
        this.orderRepository = orderRepository;
        this.vendorRepository = vendorRepository;
        this.authContext = authContext;
    }

    public List<Orders> getAllOrders() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            return Collections.emptyList();
        }
        return orderRepository.findBySupplier_Company_IdOrderByCreatedAtDesc(companyId);
    }

    public Orders getOrderById(Long id) {
        Orders order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        assertOrderInTenant(order);
        return order;
    }

    public Orders createOrder(OrderRequest req) {
        Vendor supplier = vendorRepository.findById(req.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null
                || supplier.getCompany() == null
                || !companyId.equals(supplier.getCompany().getId())) {
            throw new RuntimeException("Supplier not found");
        }

        Orders order = Orders.builder()
                .orderId(req.getOrderId())
                .orderName(req.getOrderName())
                .orderStatus(req.getOrderStatus())
                .supplier(supplier)
                .createdBy(req.getCreatedBy())
                .agreement(req.getAgreement())
                .estimatedDeliveryDate(req.getEstimatedDeliveryDate())
                .shipmentDate(req.getShipmentDate())
                .orderDate(req.getOrderDate())
                .notesRemarks(req.getNotesRemarks())
                .createdAt(Instant.now())
                .build();

        return orderRepository.save(order);
    }

    public Orders updateOrder(Long id, OrderRequest req) {
        Orders existing = getOrderById(id);
        existing.setOrderName(req.getOrderName());
        existing.setOrderStatus(req.getOrderStatus());
        existing.setNotesRemarks(req.getNotesRemarks());
        existing.setShipmentDate(req.getShipmentDate());
        existing.setEstimatedDeliveryDate(req.getEstimatedDeliveryDate());
        existing.setOrderDate(req.getOrderDate());
        return orderRepository.save(existing);
    }

    public void deleteOrder(Long id) {
        Orders existing = getOrderById(id);
        orderRepository.delete(existing);
    }

    private void assertOrderInTenant(Orders order) {
        Long cid = authContext.getCurrentCompanyId();
        if (cid == null) {
            throw new RuntimeException("Company context required");
        }
        if (order.getSupplier() == null
                || order.getSupplier().getCompany() == null
                || !cid.equals(order.getSupplier().getCompany().getId())) {
            throw new RuntimeException("Order not found or access denied");
        }
    }
}
