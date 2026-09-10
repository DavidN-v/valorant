package com.valorank.stats;

import com.valorank.stats.StatsDtos.PlayerStatsResponse;
import com.valorank.stats.StatsDtos.AgentStatResponse;
import com.valorank.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlador del Leaderboard.
 * - GET /leaderboard          -> lee datos guardados en DB, NO llama a HenrikDev
 * - GET /leaderboard/refresh-status -> estado del refresh en curso
 * - POST /leaderboard/refresh-all   -> dispara refresh manual en background (max 1 a la vez)
 * - POST /leaderboard/reset-and-refresh -> SOLO administracion, borra y reprocesa todo
 */
@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
@Slf4j
public class LeaderboardController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${henrikdev.refresh.delay-between-players-ms:3000}")
    private long delayBetweenPlayersMs;

    @Value("${henrikdev.refresh.max-players-per-run:5}")
    private int maxPlayersPerRun;

    // Estado del refresh activo
    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicInteger refreshTotal       = new AtomicInteger(0);
    private final AtomicInteger refreshCurrent     = new AtomicInteger(0);

    private final PlayerStatsRepository playerStatsRepository;
    private final UserRepository userRepository;
    private final StatsUpdateService statsUpdateService;
    private final com.valorank.notification.DiscordWebhookService discordWebhookService;

    @PostMapping("/discord/test")
    public ResponseEntity<Map<String, Object>> testDiscordWebhook() {
        boolean sent = discordWebhookService.sendTestNotification();
        return ResponseEntity.ok(Map.of(
            "success", sent,
            "message", sent ? "Notificación enviada a Discord exitosamente!" : "No se pudo enviar la notificación. Verifica la URL del Webhook."
        ));
    }

    /**
     * Devuelve el leaderboard ordenado desde la DB, SIN consultar HenrikDev.
     */
    @GetMapping
    public List<PlayerStatsResponse> leaderboard(@RequestParam(defaultValue = "rr") String sortBy) {
        List<PlayerStats> all = playerStatsRepository.findAll();

        Comparator<PlayerStats> comparator = switch (sortBy) {
            case "winrate"  -> Comparator.comparing(PlayerStats::getWinratePct,    Comparator.nullsLast(Comparator.naturalOrder()));
            case "kd"       -> Comparator.comparing(PlayerStats::getKdRatio,        Comparator.nullsLast(Comparator.naturalOrder()));
            case "headshot" -> Comparator.comparing(PlayerStats::getHeadshotPct,    Comparator.nullsLast(Comparator.naturalOrder()));
            case "acs"      -> Comparator.comparing(PlayerStats::getAvgCombatScore, Comparator.nullsLast(Comparator.naturalOrder()));
            case "matches"  -> Comparator.comparing(PlayerStats::getMatchesPlayed,  Comparator.nullsLast(Comparator.naturalOrder()));
            default         -> Comparator.comparingInt(LeaderboardController::rankScore);
        };

        return all.stream()
                .sorted(comparator.reversed())
                .map(s -> {
                    List<AgentStatResponse> agents = new ArrayList<>();
                    if (s.getTopAgentsJson() != null && !s.getTopAgentsJson().isBlank()) {
                        try {
                            agents = MAPPER.readValue(s.getTopAgentsJson(), new TypeReference<List<AgentStatResponse>>() {});
                        } catch (Exception ignored) {}
                    }
                    return new PlayerStatsResponse(
                        s.getUser().getId(),
                        s.getUser().getDisplayName(),
                        s.getUser().getRiotId(),
                        s.getCurrentTier(),
                        s.getRankingInTier(),
                        s.getPeakTier(),
                        s.getMatchesPlayed(),
                        s.getWins(),
                        s.getLosses(),
                        s.getWinratePct(),
                        s.getKdRatio(),
                        s.getHeadshotPct(),
                        s.getAvgCombatScore(),
                        agents,
                        s.getLastUpdated()
                    );
                })
                .toList();
    }

    public record RefreshStatus(boolean inProgress, int current, int total) {}

    @GetMapping("/refresh-status")
    public RefreshStatus getRefreshStatus() {
        return new RefreshStatus(
            refreshInProgress.get(),
            refreshCurrent.get(),
            refreshTotal.get()
        );
    }

    /**
     * Refresh manual: responde INMEDIATAMENTE y procesa en background.
     * Si ya hay un refresh en progreso, devuelve 409 Conflict.
     * Procesa de a lotes (max maxPlayersPerRun) con pausa entre jugadores.
     */
    @PostMapping("/refresh-all")
    public ResponseEntity<String> refreshAll() {
        if (refreshInProgress.get()) {
            log.info("Refresh-all rechazado: ya hay uno en progreso.");
            return ResponseEntity.status(409).body("Ya hay un refresh en progreso, espera un momento.");
        }

        List<PlayerStats> all = playerStatsRepository.findAll();
        if (all.isEmpty()) {
            return ResponseEntity.ok("No hay jugadores registrados aun.");
        }

        refreshTotal.set(all.size());
        refreshCurrent.set(0);

        Thread.ofVirtual().start(() -> {
            refreshInProgress.set(true);
            int ok = 0, fail = 0;
            log.info("=== [REFRESH INICIADO] {} jugadores ===", all.size());

            // Obtener acto activo UNA sola vez para todos
            com.valorank.integration.henrik.HenrikDevClient.ActInfo actInfo;
            try {
                actInfo = statsUpdateService.getCurrentActInfo();
                if (actInfo != null) {
                    log.info("  Acto activo: {}", actInfo.shortId());
                } else {
                    log.warn("  No se pudo determinar el acto activo. Se usara fallback por jugador.");
                }
            } catch (Exception e) {
                log.warn("  Error obteniendo acto activo: {}", e.getMessage());
                actInfo = null;
            }

            for (int i = 0; i < all.size(); i++) {
                PlayerStats ps = all.get(i);
                try {
                    statsUpdateService.refreshStatsFor(ps.getUser(), actInfo);
                    ok++;
                } catch (Exception e) {
                    log.warn("  [JUGADOR OMITIDO TEMPORALMENTE] {}: {}", ps.getUser().getRiotId(), e.getMessage());
                    fail++;
                    // Las estadisticas anteriores se conservan automaticamente
                }
                refreshCurrent.incrementAndGet();

                // Pausa entre jugadores para respetar rate limit
                if (i < all.size() - 1) {
                    try { Thread.sleep(delayBetweenPlayersMs); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
            }

            log.info("=== [REFRESH TERMINADO] {} OK, {} fallidos ===", ok, fail);
            refreshInProgress.set(false);
        });

        return ResponseEntity.ok("Actualizando en background...");
    }

    /**
     * SOLO USO ADMINISTRATIVO.
     * Borra todos los player_stats y los reprocesa desde cero.
     * NO borres processedMatchIdsJson en un uso normal.
     */
    @PostMapping("/reset-and-refresh")
    public ResponseEntity<String> resetAndRefresh() {
        if (refreshInProgress.get()) {
            return ResponseEntity.status(409).body("Ya hay un refresh en progreso.");
        }

        log.warn("=== [RESET] Borrando todos los player_stats ===");
        playerStatsRepository.deleteAll();

        var users = userRepository.findAll();
        int ok = 0, fail = 0;

        for (int i = 0; i < users.size(); i++) {
            var u = users.get(i);
            try {
                statsUpdateService.refreshStatsFor(u);
                ok++;
                log.info("  [OK] {}", u.getRiotId());
            } catch (Exception e) {
                log.warn("  [FAIL] {}: {}", u.getRiotId(), e.getMessage());
                fail++;
            }
            if (i < users.size() - 1) {
                try { Thread.sleep(delayBetweenPlayersMs + 500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }
        return ResponseEntity.ok(String.format("Reset completo. Actualizados: %d, Fallidos: %d", ok, fail));
    }

    // ── Score para ordenar por rango ──────────────────────────────────────────
    private static int rankScore(PlayerStats s) {
        String tierStr = s.getCurrentTier() != null ? s.getCurrentTier().toLowerCase() : "";
        int tierNum = !tierStr.isBlank() && !tierStr.equals("unrated")
                ? tierNameToNumber(tierStr)
                : (s.getPeakTier() != null ? s.getPeakTier() : 0);
        int rr = s.getRankingInTier() != null ? s.getRankingInTier() : 0;
        return tierNum * 100 + rr;
    }

    private static int tierNameToNumber(String tier) {
        if (tier.startsWith("iron 1"))       return 3;
        if (tier.startsWith("iron 2"))       return 4;
        if (tier.startsWith("iron 3"))       return 5;
        if (tier.startsWith("bronze 1"))     return 6;
        if (tier.startsWith("bronze 2"))     return 7;
        if (tier.startsWith("bronze 3"))     return 8;
        if (tier.startsWith("silver 1"))     return 9;
        if (tier.startsWith("silver 2"))     return 10;
        if (tier.startsWith("silver 3"))     return 11;
        if (tier.startsWith("gold 1"))       return 12;
        if (tier.startsWith("gold 2"))       return 13;
        if (tier.startsWith("gold 3"))       return 14;
        if (tier.startsWith("platinum 1"))   return 15;
        if (tier.startsWith("platinum 2"))   return 16;
        if (tier.startsWith("platinum 3"))   return 17;
        if (tier.startsWith("diamond 1"))    return 18;
        if (tier.startsWith("diamond 2"))    return 19;
        if (tier.startsWith("diamond 3"))    return 20;
        if (tier.startsWith("ascendant 1"))  return 21;
        if (tier.startsWith("ascendant 2"))  return 22;
        if (tier.startsWith("ascendant 3"))  return 23;
        if (tier.startsWith("immortal 1"))   return 24;
        if (tier.startsWith("immortal 2"))   return 25;
        if (tier.startsWith("immortal 3"))   return 26;
        if (tier.startsWith("radiant"))      return 27;
        return 0;
    }
}
