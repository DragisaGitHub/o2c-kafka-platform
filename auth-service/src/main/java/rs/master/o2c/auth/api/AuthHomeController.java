package rs.master.o2c.auth.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthHomeController {

    @GetMapping("/")
    public String home() {
        return "auth-service up";
    }

    @GetMapping("/mfa/ott/sent")
    public String ottSent() {
        return "OneTimeToken Sent (log only)";
    }
}