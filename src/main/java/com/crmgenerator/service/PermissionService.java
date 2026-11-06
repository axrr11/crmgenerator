package com.crmgenerator.service;

import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.domain.UserModulePermission;
import com.crmgenerator.repository.UserAccountRepository;
import com.crmgenerator.repository.UserModulePermissionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PermissionService {

    private final UserAccountRepository users;
    private final UserModulePermissionRepository permissions;

    public PermissionService(UserAccountRepository users, UserModulePermissionRepository permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    public Optional<UserAccount> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        return users.findByEmail(auth.getName());
    }

    public boolean canRead(String moduleSlug) {
        return check(moduleSlug, "read");
    }

    public boolean canCreate(String moduleSlug) {
        return check(moduleSlug, "create");
    }

    public boolean canUpdate(String moduleSlug) {
        return check(moduleSlug, "update");
    }

    public boolean canDelete(String moduleSlug) {
        return check(moduleSlug, "delete");
    }

    private boolean check(String moduleSlug, String action) {
        UserAccount u = currentUser().orElse(null);
        if (u == null) return false;
        if (u.isFullAccess()) return true;
        if (u.isReadOnlyRole()) {
            // visitor: only read on assigned modules
            if (!"read".equals(action)) return false;
            return permissions.findByUserIdAndModuleSlug(u.getId(), moduleSlug)
                    .map(UserModulePermission::isCanRead)
                    .orElse(false);
        }
        // user role
        return permissions.findByUserIdAndModuleSlug(u.getId(), moduleSlug)
                .map(p -> switch (action) {
                    case "read" -> p.isCanRead();
                    case "create" -> p.isCanCreate();
                    case "update" -> p.isCanUpdate();
                    case "delete" -> p.isCanDelete();
                    default -> false;
                })
                .orElse(false);
    }
}
