package com.erp.controller.inventory;

import com.erp.domain.inventory.Orders;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.OrderRequest;
import com.erp.service.inventory.OrderService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<Orders> getAllOrders() {
        return orderService.getAllOrders();
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ResponseEntity<Orders> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<Orders> createOrder(@RequestBody OrderRequest req) {
        return ResponseEntity.ok(orderService.createOrder(req));
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ResponseEntity<Orders> updateOrder(@PathVariable Long id, @RequestBody OrderRequest req) {
        return ResponseEntity.ok(orderService.updateOrder(id, req));
    }

    @RequiresPermission(module = AppModule.INVENTORY_PURCHASE, action = {AppAction.DELETE})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
