package com.valorank.stats;

import java.time.Instant;

import java.util.List;

public class StatsDtos {

    public record AgentStatResponse(
            String name,
            Integer matches,
            Double winrate,
            Double kd
    ) {}

    public record PlayerStatsResponse(
            Long userId,
            String displayName,
            String riotId,
            String currentTier,
            Integer rankingInTier,
            Integer peakTier,
            Integer matchesPlayed,
            Integer wins,
            Integer losses,
            Double winratePct,
            Double kdRatio,
            Double headshotPct,
            Double avgCombatScore,
            List<AgentStatResponse> topAgents,
            Instant lastUpdated
    ) {}
}

