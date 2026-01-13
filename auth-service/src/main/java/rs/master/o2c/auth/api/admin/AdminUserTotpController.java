package rs.master.o2c.auth.api.admin;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import rs.master.o2c.auth.service.TotpCryptoService;
import rs.master.o2c.auth.service.TotpEnrollmentService;
import rs.master.o2c.auth.service.TotpService;
import rs.master.o2c.auth.service.TotpUserMfaService;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/admin/users/{username}/mfa/totp")
@RequiredArgsConstructor
public class AdminUserTotpController {

    private final TotpEnrollmentService enrollments;
    private final TotpService totp;
    private final TotpCryptoService crypto;
    private final TotpUserMfaService userMfa;

    public record EnrollTotpResponse(
            String setupId,
            String username,
            String issuer,
            String label,
            Instant expiresAt
    ) {}

    public record ConfirmTotpRequest(
            @NotBlank String setupId,
            @NotBlank String code
    ) {}

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping(value = "/enroll", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<EnrollTotpResponse> enroll(@PathVariable String username) {
        String normalized = normalizeUsername(username);

        return userMfa.userExists(normalized)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(NOT_FOUND));
                    }
                    var session = enrollments.createSetup(normalized);
                    String label = session.issuer() + ":" + session.username();
                    return Mono.just(new EnrollTotpResponse(
                            session.setupId(),
                            session.username(),
                            session.issuer(),
                            label,
                            session.expiresAt()
                    ));
                });
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public Mono<ResponseEntity<byte[]>> qr(@PathVariable String username, @RequestParam(name = "setupId") String setupId) {
        String normalized = normalizeUsername(username);
        String id = requireNonBlank(setupId, "setupId");

        var session = enrollments.getValidSetup(id);
        if (session == null || !normalized.equals(session.username())) {
            return Mono.just(ResponseEntity.status(NOT_FOUND).build());
        }

        String uri = totp.buildOtpAuthUri(session.issuer(), session.username(), session.secret());
        byte[] png = renderQrPng(uri);

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, max-age=0")
                .header("Pragma", "no-cache")
                .body(png));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping(value = "/confirm")
    public Mono<ResponseEntity<Void>> confirm(@PathVariable String username, @Valid @RequestBody Mono<ConfirmTotpRequest> body) {
        String normalized = normalizeUsername(username);

        return body.flatMap(req -> {
            String setupId = requireNonBlank(req == null ? null : req.setupId(), "setupId");
            String code = requireNonBlank(req.code(), "code");

            var session = enrollments.getValidSetup(setupId);
            if (session == null || !normalized.equals(session.username())) {
                return Mono.just(ResponseEntity.status(NOT_FOUND).build());
            }

            boolean ok = totp.isValidCode(session.secret(), code);
            if (!ok) {
                return Mono.just(ResponseEntity.status(UNAUTHORIZED).build());
            }

            byte[] secretEnc = crypto.encrypt(session.secret());
            Instant now = Instant.now();

            return userMfa.enableTotp(normalized, secretEnc, now)
                    .onErrorResume(e -> {
                        if (e instanceof IllegalStateException ise && "NOT_FOUND".equals(ise.getMessage())) {
                            return Mono.error(new ResponseStatusException(NOT_FOUND));
                        }
                        return Mono.error(e);
                    })
                    .then(Mono.fromSupplier(() -> {
                        enrollments.consumeSetup(setupId);
                        return ResponseEntity.noContent().build();
                    }));
        });
    }

    private static String normalizeUsername(String username) {
        String u = username == null ? null : username.trim();
        if (u == null || u.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "username is required");
        }
        return u;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }

    private static byte[] renderQrPng(String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, 256, 256);
            var image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to generate QR", e);
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to render PNG", e);
        }
    }
}
