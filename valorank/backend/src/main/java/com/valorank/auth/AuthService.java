package com.valorank.auth;

import com.valorank.auth.AuthDtos.*;
import com.valorank.stats.StatsUpdateService;
import com.valorank.user.User;
import com.valorank.user.UserRepository;
import com.valorank.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StatsUpdateService statsUpdateService;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese email ya esta registrado");
        }
        if (userRepository.existsByRiotId(req.riotId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese Riot ID ya esta vinculado a otra cuenta");
        }
        if (!req.riotId().contains("#")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El Riot ID debe tener formato nombre#tag");
        }

        User user = User.builder()
                .displayName(req.displayName())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .riotId(req.riotId())
                .region(req.region())
                .build();

        final User savedUser = userRepository.save(user);

        // Primer fetch de stats al registrarse, de forma asincrona para no bloquear el registro
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                statsUpdateService.refreshStatsFor(savedUser);
            } catch (Exception ignored) {
                // si falla, el scheduler lo reintentara despues
            }
        });

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());
        return new AuthResponse(token, savedUser.getId(), savedUser.getDisplayName());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BadCredentialsException("Credenciales invalidas");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getId(), user.getDisplayName());
    }
}


