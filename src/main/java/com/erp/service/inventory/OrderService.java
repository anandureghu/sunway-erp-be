package com.erp.service.inventory;

import com.erp.domain.inventory.Orders;
import com.erp.domain.inventory.Vendor;
import com.erp.dto.inventory.OrderRequest;
import com.erp.repo.inventory.OrderRepository;
import com.erp.repo.inventory.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final VendorRepository vendorRepository;

    public OrderService(OrderRepository orderRepository, VendorRepository vendorRepository) {
        this.orderRepository = orderRepository;
        this.vendorRepository = vendorRepository;
    }

    public List<Orders> getAllOrders() {
        return orderRepository.findAll();
    }

    public Orders getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public Orders createOrder(OrderRequest req) {
        Vendor supplier = vendorRepository.findById(req.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

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
        orderRepository.deleteById(id);
    }
}
