package com.valorank.user;

import com.valorank.stats.StatsDtos.PlayerStatsResponse;
import com.valorank.stats.PlayerStats;
import com.valorank.user.User;
import com.valorank.stats.PlayerStatsRepository;
import com.valorank.stats.StatsUpdateService;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.ArrayList;
import com.valorank.stats.StatsDtos.AgentStatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PlayerStatsRepository playerStatsRepository;
    private final StatsUpdateService statsUpdateService;

    @GetMapping("/stats")
    public PlayerStatsResponse myStats(@AuthenticationPrincipal User user) {
        PlayerStats stats = playerStatsRepository.findByUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aun no hay stats, espera al proximo refresh"));
        return toResponse(stats);
    }

    /** Fuerza una actualizacion inmediata de mis propias stats, sin esperar al scheduler. */
    @PostMapping("/stats/refresh")
    public PlayerStatsResponse refreshMyStats(@AuthenticationPrincipal User user) {
        PlayerStats stats = statsUpdateService.refreshStatsFor(user);
        return toResponse(stats);
    }

    private PlayerStatsResponse toResponse(PlayerStats s) {
        List<AgentStatResponse> agents = new ArrayList<>();
        if (s.getTopAgentsJson() != null && !s.getTopAgentsJson().isBlank()) {
            try {
                agents = MAPPER.readValue(s.getTopAgentsJson(), new TypeReference<List<AgentStatResponse>>() {});
            } catch (Exception e) {}
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
    }
}



