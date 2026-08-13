package com.cinebook.identity.infra;

import com.cinebook.identity.domain.ForbiddenException;
import com.cinebook.identity.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Phan quyen theo doi tuong. Tach khoi chuoi SpEL cua @PreAuthorize vi luat loai nay
 * se phuc tap dan (nhan vien cua dung rap do, quan ly ca truc...) va can debug duoc,
 * unit test duoc.
 */
@Component
public class AccessGuard {

    public void requireSelfOrAdmin(UUID targetUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID callerId)) {
            throw new ForbiddenException();
        }
        if (callerId.equals(targetUserId) || hasRole(auth, Role.ADMIN)) {
            return;
        }
        throw new ForbiddenException();
    }

    private boolean hasRole(Authentication auth, Role role) {
        String authority = "ROLE_" + role.name();
        return auth.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
