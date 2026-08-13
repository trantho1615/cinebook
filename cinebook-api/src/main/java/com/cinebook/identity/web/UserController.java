package com.cinebook.identity.web;

import com.cinebook.identity.domain.UserNotFoundException;
import com.cinebook.identity.infra.AccessGuard;
import com.cinebook.identity.infra.UserRepository;
import com.cinebook.identity.web.dto.UserResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class UserController {

    private final UserRepository users;
    private final AccessGuard accessGuard;

    public UserController(UserRepository users, AccessGuard accessGuard) {
        this.users = users;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/users/{id}")
    public UserResponse getUser(@PathVariable UUID id) {
        // Kiem tra quyen TRUOC khi cham vao database: khong nap du lieu ma nguoi goi
        // khong duoc phep thay, du chi de roi nem loi.
        accessGuard.requireSelfOrAdmin(id);
        return users.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> listUsers() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }
}
