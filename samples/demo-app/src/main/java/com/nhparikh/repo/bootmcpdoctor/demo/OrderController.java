package com.nhparikh.repo.bootmcpdoctor.demo;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    private final Duration checkoutDelay;

    OrderController(@Value("${demo.checkout.delay:2s}") Duration checkoutDelay) {
        this.checkoutDelay = checkoutDelay;
    }

    @GetMapping("/orders")
    List<Order> orders() {
        return List.of(new Order("A-1001", "SHIPPED"), new Order("A-1002", "PENDING"));
    }

    // Deliberately slow so that it tops get_slow_endpoints.
    @PostMapping("/checkout")
    Order checkout() throws InterruptedException {
        long delay = this.checkoutDelay.toMillis();
        Thread.sleep(delay + ThreadLocalRandom.current().nextLong(delay / 4 + 1));
        return new Order("A-1003", "PLACED");
    }

    record Order(String id, String status) {
    }
}
