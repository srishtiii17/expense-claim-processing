package com.example.expenseclaims.web;

import com.example.expenseclaims.dto.LoginRequest;
import com.example.expenseclaims.dto.LoginResponse;
import com.example.expenseclaims.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only public endpoint: exchanges an email and password for a JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // no-store: browsers and proxies must not keep a copy of the token.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.login(request));
    }
}
