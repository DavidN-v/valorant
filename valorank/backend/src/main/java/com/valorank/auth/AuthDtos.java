package com.valorank.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String displayName,
            @Email @NotBlank String email,
            @NotBlank String password,
            @NotBlank String riotId,   // formato "nombre#tag"
            @NotBlank String region    // na, eu, latam, br, ap, kr
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String token,
            Long userId,
            String displayName
    ) {}
}

