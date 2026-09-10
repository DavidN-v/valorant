package com.valorank.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valorank.stats.StatsDtos.AgentStatResponse;
import com.valorank.stats.StatsDtos.PlayerStatsResponse;
import com.valorank.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controlador del Leaderboard de PREMIER.
 */
@RestController
@RequestMapping("/leaderboard/premier")
@RequiredArgsConstructor
@Slf4j
public class PremierController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${henrikdev.refresh.delay-between-players-ms:3000}")
    private long delayBetweenPlayersMs;

    private final AtomicBoolean refreshInProgress = new AtomicBoolean(false);
    private final AtomicInteger refreshTotal       = new AtomicInteger(0);
    private final AtomicInteger refreshCurrent     = new AtomicInteger(0);

    private final PremierStatsRepository premierStatsRepository;
    private final UserRepository userRepository;
    private final PremierStatsUpdateService premierStatsUpdateService;

    @GetMapping
    public List<PlayerStatsResponse> premierLeaderboard(@RequestParam(defaultValue = "winrate") String sortBy) {
        List<PremierStats> all = premierStatsRepository.findAll();

        Comparator<PremierStats> comparator;
        if ("winrate".equals(sortBy)) comparator = Comparator.comparing(PremierStats::getWinratePct, Comparator.nullsLast(Comparator.naturalOrder()));
        else if ("kd".equals(sortBy)) comparator = Comparator.comparing(PremierStats::getKdRatio, Comparator.nullsLast(Comparator.naturalOrder()));
        else if ("headshot".equals(sortBy)) comparator = Comparator.comparing(PremierStats::getHeadshotPct, Comparator.nullsLast(Comparator.naturalOrder()));
        else if ("acs".equals(sortBy)) comparator = Comparator.comparing(PremierStats::getAvgCombatScore, Comparator.nullsLast(Comparator.naturalOrder()));
        else if ("matches".equals(sortBy)) comparator = Comparator.comparing(PremierStats::getMatchesPlayed, Comparator.nullsLast(Comparator.naturalOrder()));
        else comparator = Comparator.comparing(PremierStats::getWinratePct, Comparator.nullsLast(Comparator.naturalOrder()));

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

    @PostMapping("/refresh-all")
    public ResponseEntity<String> refreshAll() {
        if (refreshInProgress.get()) {
            return ResponseEntity.status(409).body("Ya hay un refresh de premier en progreso.");
        }

        var users = userRepository.findAll();
        if (users.isEmpty()) {
            return ResponseEntity.ok("No hay jugadores registrados aun.");
        }

        refreshTotal.set(users.size());
        refreshCurrent.set(0);

        Thread.ofVirtual().start(() -> {
            refreshInProgress.set(true);
            int ok = 0, fail = 0;
            log.info("=== [REFRESH PREMIER INICIADO] {} jugadores ===", users.size());

            for (int i = 0; i < users.size(); i++) {
                var u = users.get(i);
                try {
                    premierStatsUpdateService.refreshPremierStatsFor(u);
                    ok++;
                } catch (Exception e) {
                    log.warn("  [JUGADOR OMITIDO PREMIER] {}: {}", u.getRiotId(), e.getMessage());
                    fail++;
                }
                refreshCurrent.incrementAndGet();

                if (i < users.size() - 1) {
                    try { Thread.sleep(delayBetweenPlayersMs); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
            }

            log.info("=== [REFRESH PREMIER TERMINADO] {} OK, {} fallidos ===", ok, fail);
            refreshInProgress.set(false);
        });

        return ResponseEntity.ok("Actualizando premier en background...");
    }
}
