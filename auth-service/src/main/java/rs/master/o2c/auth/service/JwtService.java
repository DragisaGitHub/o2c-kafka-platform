package rs.master.o2c.auth.service;

import java.util.Collection;

public interface JwtService {
    String generateAccessToken(String username, Collection<String> roles);
}