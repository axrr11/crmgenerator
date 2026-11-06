package com.crmgenerator.service;

import com.crmgenerator.domain.Tenant;
import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.generator.support.Identifier;
import com.crmgenerator.repository.TenantRepository;
import com.crmgenerator.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;

    public RegistrationService(TenantRepository tenants, UserAccountRepository users, PasswordEncoder encoder) {
        this.tenants = tenants;
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public UserAccount registerBusiness(String businessName, String ownerName, String email, String password, String phone) {
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("El nombre del negocio es obligatorio");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Tu nombre es obligatorio");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres");
        }
        if (users.existsByEmail(email.trim().toLowerCase())) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese email");
        }

        String slug = Identifier.slugify(businessName);
        // ensure unique tenant slug
        String base = slug;
        int i = 1;
        while (tenants.existsBySlug(slug)) {
            slug = base + "_" + i++;
        }

        Tenant t = new Tenant();
        t.setName(businessName.trim());
        t.setSlug(slug);
        t.setEmail(email.trim().toLowerCase());
        t.setPhone(phone);
        t.setStatus("active");
        tenants.save(t);

        UserAccount u = new UserAccount();
        u.setTenantId(t.getId());
        u.setName(ownerName.trim());
        u.setEmail(email.trim().toLowerCase());
        u.setPassword(encoder.encode(password));
        u.setRole("owner");
        users.save(u);
        return u;
    }
}
