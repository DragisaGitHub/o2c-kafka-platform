package rs.master.o2c.auth.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InMemoryUsers {
    private final Map<String, UserRecord> users = Map.of(
            "user", new UserRecord("user", "password"),
            "admin", new UserRecord("admin", "password")
    );

    public boolean validCredentials(String username, String password) {
        UserRecord u = users.get(username);
        return u != null && u.password().equals(password);
    }

    public record UserRecord(String username, String password) {}
}