package rs.master.o2c.checkout.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import rs.master.o2c.checkout.api.dto.CheckoutStatusDto;
import rs.master.o2c.checkout.persistence.repository.CheckoutRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/checkouts")
public class CheckoutStatusController {

    private final CheckoutRepository checkoutRepository;

    public CheckoutStatusController(CheckoutRepository checkoutRepository) {
        this.checkoutRepository = checkoutRepository;
    }

    @GetMapping("/status")
    public Flux<CheckoutStatusDto> status(@RequestParam("orderIds") String orderIds) {
        List<String> parsed = parseOrderIds(orderIds);
        return checkoutRepository.findByOrderIdIn(parsed)
                .map(e -> new CheckoutStatusDto(e.orderId(), e.status()));
    }

    private static List<String> parseOrderIds(String orderIds) {
        if (orderIds == null || orderIds.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderIds is required");
        }

        String[] parts = orderIds.split(",");
        Set<String> distinct = new LinkedHashSet<>();
        for (String raw : parts) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isEmpty()) continue;
            try {
                UUID.fromString(trimmed);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderIds must be UUIDs");
            }
            distinct.add(trimmed);
        }

        if (distinct.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderIds must not be empty");
        }

        return new ArrayList<>(distinct);
    }
}
