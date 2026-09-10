package com.valorank.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "riot_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password; // hasheado con BCrypt

    // Formato "nombre#tag", ej: "Sombra#LAN"
    @Column(name = "riot_id", nullable = false, unique = true)
    private String riotId;

    // Region para la consulta a HenrikDev: na, eu, latam, br, ap, kr
    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

