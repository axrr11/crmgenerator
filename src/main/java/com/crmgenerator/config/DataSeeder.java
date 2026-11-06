package com.crmgenerator.config;

import com.crmgenerator.domain.Tenant;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.repository.TenantRepository;
import com.crmgenerator.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seedAdmin(UserAccountRepository users, TenantRepository tenants, PasswordEncoder encoder) {
        return args -> {
            Tenant platform = tenants.findBySlug("platform").orElseGet(() -> {
                Tenant t = new Tenant();
                t.setName("Plataforma CRM");
                t.setSlug("platform");
                t.setEmail("admin@crm.local");
                t.setStatus("active");
                return tenants.save(t);
            });

            if (!users.existsByEmail("admin@crm.local")) {
                UserAccount u = new UserAccount();
                u.setName("Creador Plataforma");
                u.setEmail("admin@crm.local");
                u.setPassword(encoder.encode("password"));
                u.setRole("creator");
                u.setTenantId(platform.getId());
                users.save(u);
            } else {
                users.findByEmail("admin@crm.local").ifPresent(u -> {
                    boolean ch = false;
                    if ("admin".equalsIgnoreCase(u.getRole())) { u.setRole("creator"); ch = true; }
                    if (u.getTenantId() == null) { u.setTenantId(platform.getId()); ch = true; }
                    if (ch) users.save(u);
                });
            }

            if (!tenants.existsBySlug("demo")) {
                Tenant t = new Tenant();
                t.setName("Negocio Demo");
                t.setSlug("demo");
                t.setEmail("dueno@demo.local");
                t.setStatus("active");
                tenants.save(t);

                if (!users.existsByEmail("dueno@demo.local")) {
                    UserAccount o = new UserAccount();
                    o.setTenantId(t.getId());
                    o.setName("Dueño Demo");
                    o.setEmail("dueno@demo.local");
                    o.setPassword(encoder.encode("password"));
                    o.setRole("owner");
                    users.save(o);
                }
            }
        };
    }
}
