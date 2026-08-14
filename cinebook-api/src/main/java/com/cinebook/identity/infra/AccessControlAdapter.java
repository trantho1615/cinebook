package com.cinebook.identity.infra;

import com.cinebook.identity.api.AccessControl;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Noi interface cong khai identity.api.AccessControl vao AccessGuard san co.
 *
 * AccessGuard van o identity.infra va khong lo ra ngoai module; ForbiddenException
 * ma no nem da duoc IdentityExceptionHandler anh xa thanh 403, nen booking khong
 * can biet gi ve kieu exception do.
 */
@Component
public class AccessControlAdapter implements AccessControl {

    private final AccessGuard accessGuard;

    public AccessControlAdapter(AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @Override
    public void requireSelfOrAdmin(UUID ownerId) {
        accessGuard.requireSelfOrAdmin(ownerId);
    }
}
