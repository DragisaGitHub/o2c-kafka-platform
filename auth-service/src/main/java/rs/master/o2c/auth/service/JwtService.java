package rs.master.o2c.auth.service;

public interface JwtService {
    String generateAccessToken(String username);
}