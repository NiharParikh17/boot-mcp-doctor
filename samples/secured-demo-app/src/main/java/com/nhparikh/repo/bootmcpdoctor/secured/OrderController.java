package com.nhparikh.repo.bootmcpdoctor.secured;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    @GetMapping("/orders")
    List<Order> orders() {
        return List.of(new Order("A-1001", "SHIPPED"), new Order("A-1002", "PENDING"));
    }

    record Order(String id, String status) {
    }
}
