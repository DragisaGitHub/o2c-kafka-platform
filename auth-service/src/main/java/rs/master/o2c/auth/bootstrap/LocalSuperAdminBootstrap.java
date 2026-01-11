package rs.master.o2c.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import rs.master.o2c.auth.config.AuthBootstrapProperties;
import rs.master.o2c.auth.service.AdminUserService;

import java.util.List;

@Component
@Profile("local")
public class LocalSuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSuperAdminBootstrap.class);

    private final AdminUserService adminUserService;
    private final AuthBootstrapProperties bootstrapProps;

    public LocalSuperAdminBootstrap(AdminUserService adminUserService, AuthBootstrapProperties bootstrapProps) {
        this.adminUserService = adminUserService;
        this.bootstrapProps = bootstrapProps;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean hasSuperAdmin = adminUserService.hasAnySuperAdmin().block();
        if (Boolean.TRUE.equals(hasSuperAdmin)) {
            log.info("[AUTH] SUPER_ADMIN already exists; bootstrap skipped");
            return;
        }

        String username = bootstrapProps.username();
        String password = bootstrapProps.password();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("No SUPER_ADMIN exists. Set auth.bootstrap.username and auth.bootstrap.password (or AUTH_BOOTSTRAP_USERNAME/AUTH_BOOTSTRAP_PASSWORD) to bootstrap one.");
        }

        adminUserService.createUser(username, password, List.of("SUPER_ADMIN"))
                .doOnSuccess(u -> log.info("[AUTH] Bootstrapped SUPER_ADMIN user='{}'", u.username()))
                .block();
    }
}
