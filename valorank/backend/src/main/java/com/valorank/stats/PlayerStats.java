package com.valorank.stats;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.valorank.user.User;

import java.time.Instant;

@Entity
@Table(name = "player_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String currentTier;      // ej: "Diamond 2"
    private Integer rankingInTier;   // RR
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

    /** IDs de partidas ya procesadas (JSON array). Maximo 200 IDs para no crecer indefinidamente. */
    @Column(columnDefinition = "TEXT")
    private String processedMatchIdsJson;

    private Instant lastUpdated;
}
