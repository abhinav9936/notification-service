package com.notificationservice.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Enforces that a TENANT_ADMIN can only act on their own tenant's resources. Role checks
 * (@PreAuthorize) confirm *what kind* of user is calling; this confirms *which tenant* they're
 * allowed to touch, which is a data-ownership check, not a role check.
 */
@Component
public class TenantAccessGuard {

    public void requireOwnTenant(AppUserPrincipal principal, Long tenantId) {
        if (principal.getTenantId() == null || !principal.getTenantId().equals(tenantId)) {
            throw new AccessDeniedException("Not authorized for tenant " + tenantId);
        }
    }
}
