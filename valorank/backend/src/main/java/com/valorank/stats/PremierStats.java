package com.valorank.stats;

import com.valorank.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entidad para guardar las estadísticas de PREMIER de un jugador.
 * Separada completamente del modo competitivo.
 */
@Entity
@Table(name = "premier_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PremierStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 50)
    private String currentTier;

    private Integer rankingInTier;

    private Integer peakTier;

    private Integer matchesPlayed;

    private Integer wins;

    private Integer losses;

    private Double winratePct;

    private Double kdRatio;

    private Double headshotPct;

    private Double avgCombatScore;

    @Column(columnDefinition = "TEXT")
    private String topAgentsJson;

    @Column(columnDefinition = "TEXT")
    private String processedMatchIdsJson;

    @CreationTimestamp
    @Column(updatable = false)
    private java.time.Instant createdAt;

    @UpdateTimestamp
    private java.time.Instant lastUpdated;

}
