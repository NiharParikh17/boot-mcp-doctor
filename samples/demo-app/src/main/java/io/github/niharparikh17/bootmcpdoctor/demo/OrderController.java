package io.github.niharparikh17.bootmcpdoctor.demo;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    @GetMapping("/orders")
    List<Order> orders() {
        return List.of(new Order("A-1001", "SHIPPED"), new Order("A-1002", "PENDING"));
    }

    // Deliberately slow so that latency shows up in the http.server.requests metric.
    @PostMapping("/checkout")
    Order checkout() throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(1500, 2500));
        return new Order("A-1003", "PLACED");
    }

    record Order(String id, String status) {
    }
}
