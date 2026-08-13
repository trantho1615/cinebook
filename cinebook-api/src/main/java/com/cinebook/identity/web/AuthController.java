package com.cinebook.identity.web;

import com.cinebook.identity.domain.EmailAlreadyUsedException;
import com.cinebook.identity.domain.InvalidCredentialsException;
import com.cinebook.identity.domain.InvalidRefreshTokenException;
import com.cinebook.identity.domain.User;
import com.cinebook.identity.infra.RefreshTokenStore;
import com.cinebook.identity.infra.TokenService;
import com.cinebook.identity.infra.UserRepository;
import com.cinebook.identity.web.dto.LoginRequest;
import com.cinebook.identity.web.dto.RefreshRequest;
import com.cinebook.identity.web.dto.RegisterRequest;
import com.cinebook.identity.web.dto.TokenResponse;
import com.cinebook.identity.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final RefreshTokenStore refreshTokens;

    public AuthController(UserRepository users,
                          PasswordEncoder passwordEncoder,
                          TokenService tokens,
                          RefreshTokenStore refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = User.register(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.phone());
        return UserResponse.from(users.save(user));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmail(User.normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        return new TokenResponse(
                tokens.issueAccessToken(user),
                refreshTokens.issueForNewSession(user.getId()),
                tokens.accessTtlSeconds());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenStore.Rotation rotation = refreshTokens.rotate(request.refreshToken());
        User user = users.findById(rotation.userId()).orElseThrow(InvalidRefreshTokenException::new);

        return new TokenResponse(
                tokens.issueAccessToken(user),
                rotation.newRawToken(),
                tokens.accessTtlSeconds());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokens.revokeFamilyOf(request.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UUID userId) {
        return UserResponse.from(users.findById(userId).orElseThrow(InvalidCredentialsException::new));
    }
}
