package com.valorank.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.valorank.user.User;
import com.valorank.integration.henrik.HenrikDevClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Servicio de actualizacion de estadisticas con:
 * - Persistencia de IDs de partidas procesadas (evita re-consultar las mismas partidas)
 * - Retry con backoff exponencial ante rate limit
 * - Fallback a lifetime stats si el jugador no jugo en el acto actual
 * - Conserva estadisticas anteriores si la actualizacion falla
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatsUpdateService {

    private final HenrikDevClient henrikDevClient;
    private final PlayerStatsRepository playerStatsRepository;
    private final com.valorank.notification.DiscordWebhookService discordWebhookService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${henrikdev.refresh.delay-between-matches-ms:2000}")
    private long delayBetweenMatchesMs;

    @Value("${henrikdev.refresh.max-processed-match-ids:200}")
    private int maxProcessedMatchIds;

    @Value("${henrikdev.refresh.rate-limit-backoff-ms:10000}")
    private long rateLimitBackoffMs;

    @Value("${henrikdev.refresh.max-retries:3}")
    private int maxRetries;

    // ── Acceso al acto activo ───────────────────────────────────────────────────
    public HenrikDevClient.ActInfo getCurrentActInfo() {
        return henrikDevClient.getCurrentActInfo();
    }

    public String getCurrentSeasonId() {
        return henrikDevClient.getCurrentSeasonId();
    }

    // ── Entry points ───────────────────────────────────────────────────────────
    public PlayerStats refreshStatsFor(User user) {
        return refreshStatsFor(user, null);
    }

    /**
     * Actualiza estadisticas de un jugador de forma incremental:
     * 1. Consulta MMR actual (rango, RR)
     * 2. Si no hay partidas nuevas -> actualiza solo rango/RR
     * 3. Si hay partidas nuevas -> descarga SOLO las no procesadas
     * 4. Conserva estadisticas anteriores si falla
     */
    public PlayerStats refreshStatsFor(User user, HenrikDevClient.ActInfo actInfoHint) {
        int hashIdx = user.getRiotId().lastIndexOf('#');
        String name = hashIdx > 0 ? user.getRiotId().substring(0, hashIdx) : user.getRiotId();
        String tag  = hashIdx > 0 ? user.getRiotId().substring(hashIdx + 1) : "";

        log.info("[JUGADOR INICIADO] '{}#{}' region={}", name, tag, user.getRegion());

        PlayerStats existing = playerStatsRepository.findByUser(user).orElse(null);

        // ── 1. MMR v2: rango actual, RR, peak ────────────────────────────────
        JsonNode mmrData;
        try {
            JsonNode mmrRoot = henrikDevClient.getMmr(user.getRegion(), name, tag);
            mmrData = mmrRoot.path("data");
        } catch (Exception e) {
            log.warn("[JUGADOR OMITIDO TEMPORALMENTE] '{}': error al consultar MMR: {}", user.getRiotId(), e.getMessage());
            // Conservar estadisticas anteriores
            return existing != null ? existing : null;
        }

        String currentTier = mmrData.path("current_data").path("currenttierpatched").asText("").trim();
        if (currentTier.isBlank()) currentTier = "Unrated";
        int rr       = mmrData.path("current_data").path("ranking_in_tier").asInt(0);
        int peakTier = mmrData.path("highest_rank").path("tier").asInt(0);
        log.info("  Rango actual: {} {} RR | peak tier={}", currentTier, rr, peakTier);

        // ── 2. Detectar temporada activa ──────────────────────────────────────
        HenrikDevClient.ActInfo activeAct = actInfoHint != null ? actInfoHint : henrikDevClient.getCurrentActInfo();
        String seasonShortId = activeAct != null ? activeAct.shortId() : null;

        int actTotalMatches = 0;
        int actTotalWins    = 0;

        JsonNode bySeason = mmrData.path("by_season");
        
        // 2.1 Intentar con la temporada global actual
        if (seasonShortId != null && bySeason.isObject() && bySeason.has(seasonShortId)) {
            JsonNode sd = bySeason.path(seasonShortId);
            if (!sd.has("error") && sd.has("number_of_games") && sd.path("number_of_games").asInt(0) > 0) {
                actTotalMatches = sd.path("number_of_games").asInt(0);
                actTotalWins    = sd.path("wins").asInt(0);
            }
        }

        // 2.2 Si no jugo en la temporada actual, Tracker.gg por defecto muestra su acto jugado mas reciente
        if (actTotalMatches == 0 && bySeason.isObject()) {
            String mostRecentSeason = null;
            int mostRecentMatches = 0;
            int mostRecentWins = 0;
            
            var fields = bySeason.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode sd = entry.getValue();
                if (!sd.has("error") && sd.has("number_of_games") && sd.path("number_of_games").asInt(0) > 0) {
                    mostRecentSeason = entry.getKey();
                    mostRecentMatches = sd.path("number_of_games").asInt(0);
                    mostRecentWins = sd.path("wins").asInt(0);
                }
            }
            
            if (mostRecentSeason != null) {
                seasonShortId = mostRecentSeason;
                actTotalMatches = mostRecentMatches;
                actTotalWins = mostRecentWins;
                log.info("  No hay partidas en el acto actual. Usando su acto mas reciente jugado: {}", seasonShortId);
            }
        }

        String oldTier = existing != null ? existing.getCurrentTier() : null;

        // ── 3. Comprobar si hay partidas nuevas ───────────────────────────────
        int savedMatches = (existing != null && existing.getMatchesPlayed() != null) ? existing.getMatchesPlayed() : 0;

        if (actTotalMatches > 0 && actTotalMatches == savedMatches) {
            log.info("  [NO HAY PARTIDAS NUEVAS] {} partidas ya registradas. Actualizando solo rango/RR.", savedMatches);
            PlayerStats result = saveStatsKeepingCombat(existing, user, currentTier, rr, peakTier,
                    savedMatches, existing.getWins(), existing.getLosses());
            checkAndNotifyRankChange(user, oldTier, result);
            return result;
        }

        if (actTotalMatches == 0) {
            log.info("  No hay partidas en el acto actual ({}). Usando stats lifetime como fallback.", seasonShortId);
        } else {
            log.info("  [PARTIDAS NUEVAS ENCONTRADAS] Guardadas={}, Act={}. Procesando diferencia.", savedMatches, actTotalMatches);
        }

        sleep(1200);

        // ── 4. Leer IDs ya procesados ─────────────────────────────────────────
        Set<String> alreadyProcessed = loadProcessedMatchIds(existing);
        log.info("  {} partidas ya procesadas omitidas.", alreadyProcessed.size());

        // ── 5. Obtener nuevas partidas ────────────────────────────────────────
        CombatAggregate newAgg = null;

        if (actTotalMatches > 0 && seasonShortId != null) {
            newAgg = fetchNewMatchesForSeason(user, name, tag, seasonShortId, actTotalMatches, alreadyProcessed);
        }

        // Fallback a lifetime si no hubo datos del acto actual
        if (newAgg == null || newAgg.matches() == 0) {
            log.info("  Calculando stats LIFETIME sin filtro de temporada (fallback).");
            newAgg = fetchLifetimeFallback(user.getRegion(), name, tag, alreadyProcessed);
            if (newAgg.matches() > 0 && actTotalMatches == 0) {
                actTotalMatches = newAgg.matches();
                actTotalWins    = newAgg.wins();
            }
        }

        // ── 6. Fusionar con estadisticas anteriores ───────────────────────────
        CombatAggregate merged = mergeWithExisting(existing, newAgg, alreadyProcessed);

        int finalMatches = actTotalMatches > 0 ? actTotalMatches : merged.matches();
        int finalWins    = actTotalMatches > 0 ? actTotalWins    : merged.wins();

        log.info("  RESULTADO FINAL: KD={} | HS%={}% | ACS={} | Partidas={}",
                String.format("%.2f", merged.kd()),
                String.format("%.1f", merged.headshotPct()),
                String.format("%.1f", merged.avgCombatScore()),
                finalMatches);

        // ── 7. Persistir ──────────────────────────────────────────────────────
        String updatedProcessedIds = serializeProcessedIds(merged.processedMatchIds());
        PlayerStats result = saveStats(existing, user, currentTier, rr, peakTier,
                finalMatches, finalWins, finalMatches - finalWins,
                merged.kd(), merged.headshotPct(), merged.avgCombatScore(),
                merged.topAgentsJson(), updatedProcessedIds);

        checkAndNotifyRankChange(user, oldTier, result);

        log.info("[JUGADOR ACTUALIZADO CORRECTAMENTE] '{}'", user.getRiotId());
        return result;
    }

    private void checkAndNotifyRankChange(User user, String oldTier, PlayerStats updatedStats) {
        if (updatedStats == null || oldTier == null || oldTier.isBlank() || oldTier.equalsIgnoreCase("unrated")) {
            return;
        }
        String newTier = updatedStats.getCurrentTier();
        if (newTier == null || newTier.isBlank() || newTier.equalsIgnoreCase("unrated")) {
            return;
        }

        int oldTierNum = com.valorank.notification.DiscordWebhookService.tierNameToNumber(oldTier);
        int newTierNum = com.valorank.notification.DiscordWebhookService.tierNameToNumber(newTier);

        if (oldTierNum > 0 && newTierNum > 0 && oldTierNum != newTierNum) {
            discordWebhookService.sendRankChangeNotification(
                    user.getRiotId(),
                    oldTier,
                    newTier,
                    newTierNum,
                    updatedStats.getRankingInTier() != null ? updatedStats.getRankingInTier() : 0,
                    updatedStats.getKdRatio(),
                    updatedStats.getHeadshotPct(),
                    updatedStats.getWinratePct(),
                    updatedStats.getMatchesPlayed(),
                    updatedStats.getWins(),
                    updatedStats.getLosses()
            );
        }
    }

    // ── Helpers de fetch ───────────────────────────────────────────────────────

    private CombatAggregate fetchNewMatchesForSeason(User user, String name, String tag,
                                                       String seasonShortId, int actTotalMatches,
                                                       Set<String> alreadyProcessed) {
        // 1. Obtener TODAS las partidas del acto competitivo usando getAllLifetimeMatches
        try {
            JsonNode lifetimeMatches = henrikDevClient.getAllLifetimeMatches(
                    user.getRegion(), name, tag, seasonShortId, "competitive");
            if (lifetimeMatches != null && lifetimeMatches.isArray() && lifetimeMatches.size() > 0) {
                log.info("  [COMPETITIVO] Obtenidas {} partidas en lifetime/matches para acto={}",
                        lifetimeMatches.size(), seasonShortId);
                // Recalcular TODAS las partidas del acto para obtener K/D, HS% y ACS globales exactos del acto
                return aggregateLifetimeStats(lifetimeMatches, seasonShortId, null);
            }
        } catch (Exception e) {
            log.warn("  Error obteniendo lifetime matches para acto {}: {}", seasonShortId, e.getMessage());
        }

        // 2. Fallback secundario: descargar match IDs incrementales con mmr-history
        List<String> allMatchIds;
        try {
            allMatchIds = henrikDevClient.getSeasonMatchIds(user.getRegion(), name, tag, seasonShortId);
        } catch (Exception e) {
            log.warn("  Error obteniendo match IDs: {}", e.getMessage());
            allMatchIds = Collections.emptyList();
        }

        List<String> newMatchIds = allMatchIds.stream()
                .filter(id -> !alreadyProcessed.contains(id))
                .collect(Collectors.toList());

        log.info("  {} partidas en el acto | {} ya procesadas | {} nuevas a descargar",
                allMatchIds.size(), alreadyProcessed.size(), newMatchIds.size());

        if (!newMatchIds.isEmpty()) {
            return aggregateFromMatchIds(newMatchIds, name, tag, alreadyProcessed);
        }
        return null;
    }

    private CombatAggregate fetchLifetimeFallback(String region, String name, String tag,
                                                    Set<String> alreadyProcessed) {
        try {
            JsonNode lifetimeMatches = henrikDevClient.getAllLifetimeMatches(region, name, tag, null, "competitive");
            return aggregateLifetimeStats(lifetimeMatches, null, null);
        } catch (Exception e) {
            log.warn("  Error en lifetime fallback sin filtro: {}", e.getMessage());
            return new CombatAggregate(0, 0, 0.0, 0.0, 0.0, "[]", new HashSet<>());
        }
    }

    // ── Agregacion desde match IDs (v2/match/{id}) ─────────────────────────────
    private CombatAggregate aggregateFromMatchIds(List<String> matchIds, String name, String tag,
                                                   Set<String> alreadyProcessed) {
        int    matchCount = 0, wins = 0;
        double kills = 0, deaths = 0;
        double headshots = 0, bodyshots = 0, legshots = 0;
        double acsSum = 0;
        Set<String> successfulIds = new HashSet<>();
        Map<String, AgentTracker> agentMap = new HashMap<>();

        for (int i = 0; i < matchIds.size(); i++) {
            String matchId = matchIds.get(i);
            if (alreadyProcessed.contains(matchId)) continue;

            JsonNode data = fetchMatchWithRetry(matchId);
            if (data == null) {
                log.warn("  Partida {} omitida (no se pudo descargar).", matchId.substring(0, Math.min(8, matchId.length())));
                continue;
            }

            int roundsPlayed = data.path("metadata").path("rounds_played").asInt(0);
            if (roundsPlayed == 0) {
                int red  = data.path("teams").path("red").path("rounds_won").asInt(0);
                int blue = data.path("teams").path("blue").path("rounds_won").asInt(0);
                roundsPlayed = red + blue;
            }
            if (roundsPlayed == 0) roundsPlayed = 1;

            JsonNode allPlayers = data.path("players").path("all_players");
            boolean found = false;
            for (JsonNode p : allPlayers) {
                if (p.path("name").asText("").equalsIgnoreCase(name)
                        && p.path("tag").asText("").equalsIgnoreCase(tag)) {

                    JsonNode s = p.path("stats");
                    int mk = s.path("kills").asInt(0);
                    int md = s.path("deaths").asInt(0);
                    int ms = s.path("score").asInt(0);
                    int mh = s.path("headshots").asInt(0);
                    int mb = s.path("bodyshots").asInt(0);
                    int ml = s.path("legshots").asInt(0);

                    kills     += mk;
                    deaths    += md;
                    headshots += mh;
                    bodyshots += mb;
                    legshots  += ml;
                    acsSum    += (double) ms / roundsPlayed;

                    String playerTeam = p.path("team").asText("").toLowerCase();
                    boolean redWon  = data.path("teams").path("red").path("has_won").asBoolean(false);
                    boolean blueWon = data.path("teams").path("blue").path("has_won").asBoolean(false);
                    boolean hasWon  = ("red".equals(playerTeam) && redWon) || ("blue".equals(playerTeam) && blueWon);
                    if (hasWon) wins++;

                    String agentName = p.path("character").asText("");
                    if (!agentName.isBlank()) {
                        AgentTracker at = agentMap.computeIfAbsent(agentName, k -> new AgentTracker());
                        at.matches++;
                        at.kills  += mk;
                        at.deaths += md;
                        if (hasWon) at.wins++;
                    }

                    matchCount++;
                    found = true;
                    successfulIds.add(matchId); // Marcar como procesada SOLO si se encontro al jugador
                    break;
                }
            }
            if (!found) {
                log.warn("  Jugador no encontrado en partida {}.", matchId.substring(0, Math.min(8, matchId.length())));
            }

            if (i < matchIds.size() - 1) sleep(delayBetweenMatchesMs);
        }

        return buildAggregate(matchCount, wins, kills, deaths, headshots, bodyshots, legshots, acsSum, agentMap, successfulIds);
    }

    // ── Agregacion desde lifetime/matches ──────────────────────────────────────
    private CombatAggregate aggregateLifetimeStats(JsonNode matches, String expectedSeason,
                                                    Set<String> alreadyProcessed) {
        if (!matches.isArray() || matches.size() == 0) {
            return new CombatAggregate(0, 0, 0.0, 0.0, 0.0, "[]", new HashSet<>());
        }

        int    matchCount = 0, wins = 0, skipped = 0;
        double kills = 0, deaths = 0;
        double headshots = 0, bodyshots = 0, legshots = 0;
        double acsSum = 0;
        Set<String> successfulIds = new HashSet<>();
        Map<String, AgentTracker> agentMap = new HashMap<>();

        for (JsonNode match : matches) {
            JsonNode s    = match.path("stats");
            JsonNode t    = match.path("teams");
            JsonNode meta = match.path("meta");
            if (s.isMissingNode()) continue;

            String matchId = meta.path("id").asText("");

            // Filtrar por temporada si se especifica
            if (expectedSeason != null && !expectedSeason.isBlank()) {
                String matchSeason = meta.path("season").path("short").asText("");
                if (!expectedSeason.equals(matchSeason)) { skipped++; continue; }
            }

            // Omitir si ya fue procesada (solo si se pasa un conjunto para filtrar)
            if (alreadyProcessed != null && !matchId.isBlank() && alreadyProcessed.contains(matchId)) continue;

            matchCount++;

            int redRounds   = t.path("red").asInt(0);
            int blueRounds  = t.path("blue").asInt(0);
            int totalRounds = Math.max(1, redRounds + blueRounds);

            kills     += s.path("kills").asInt(0);
            deaths    += s.path("deaths").asInt(0);
            headshots += s.path("shots").path("head").asInt(0);
            bodyshots += s.path("shots").path("body").asInt(0);
            legshots  += s.path("shots").path("leg").asInt(0);
            acsSum    += (double) s.path("score").asInt(0) / totalRounds;

            String team = s.path("team").asText("").toLowerCase();
            boolean hasWon = ("red".equals(team) && redRounds > blueRounds) || ("blue".equals(team) && blueRounds > redRounds);
            if (hasWon) wins++;

            String agentName = s.path("character").path("name").asText("");
            if (!agentName.isBlank()) {
                AgentTracker at = agentMap.computeIfAbsent(agentName, k -> new AgentTracker());
                at.matches++;
                at.kills  += s.path("kills").asInt(0);
                at.deaths += s.path("deaths").asInt(0);
                if (hasWon) at.wins++;
            }

            if (!matchId.isBlank()) successfulIds.add(matchId);
        }

        if (skipped > 0) log.info("  {} partidas de otras temporadas omitidas.", skipped);

        return buildAggregate(matchCount, wins, kills, deaths, headshots, bodyshots, legshots, acsSum, agentMap, successfulIds);
    }

    // ── Fetch con retry y backoff ──────────────────────────────────────────────
    private JsonNode fetchMatchWithRetry(String matchId) {
        long backoff = rateLimitBackoffMs;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                JsonNode matchRoot = henrikDevClient.getMatchById(matchId);
                return matchRoot.path("data");
            } catch (RuntimeException e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("Rate limit") || msg.contains("429")) {
                    log.warn("  [RATE LIMIT ALCANZADO] Esperando {}s antes de reintentar (intento {}/{})...",
                            backoff / 1000, attempt, maxRetries);
                    sleep(backoff);
                    backoff *= 2; // Backoff exponencial: 10s, 20s, 40s
                } else {
                    log.warn("  Error en partida {}: {}", matchId.substring(0, Math.min(8, matchId.length())), msg);
                    return null;
                }
            } catch (Exception e) {
                log.warn("  Error inesperado en partida {}: {}", matchId.substring(0, Math.min(8, matchId.length())), e.getMessage());
                return null;
            }
        }
        log.warn("  [JUGADOR OMITIDO TEMPORALMENTE] No se pudo obtener partida {} tras {} intentos.",
                matchId.substring(0, Math.min(8, matchId.length())), maxRetries);
        return null;
    }

    // ── Fusion de estadisticas nuevas con las existentes ──────────────────────
    private CombatAggregate mergeWithExisting(PlayerStats existing, CombatAggregate newAgg,
                                               Set<String> alreadyProcessed) {
        if (existing == null || newAgg == null) return newAgg != null ? newAgg : new CombatAggregate(0, 0, 0.0, 0.0, 0.0, "[]", new HashSet<>());

        // Combinar IDs procesados
        Set<String> allIds = new HashSet<>(alreadyProcessed);
        allIds.addAll(newAgg.processedMatchIds());

        int prevMatches = existing.getMatchesPlayed() != null ? existing.getMatchesPlayed() : 0;
        int newMatches = newAgg.matches();

        // 1. Si no habia datos previos o newAgg contiene el total completo del acto
        if (prevMatches == 0 || newMatches >= prevMatches) {
            return new CombatAggregate(newAgg.matches(), newAgg.wins(), newAgg.kd(),
                    newAgg.headshotPct(), newAgg.avgCombatScore(), newAgg.topAgentsJson(), allIds);
        }

        // 2. Si newAgg solo contiene partidas incrementales nuevas descargadas (ej. 2 de 76)
        if (newMatches > 0) {
            int totalMatches = prevMatches + newMatches;
            double prevKd = existing.getKdRatio() != null ? existing.getKdRatio() : 0.0;
            double prevHs = existing.getHeadshotPct() != null ? existing.getHeadshotPct() : 0.0;
            double prevAcs = existing.getAvgCombatScore() != null ? existing.getAvgCombatScore() : 0.0;

            double weightedKd = ((prevKd * prevMatches) + (newAgg.kd() * newMatches)) / totalMatches;
            double weightedHs = ((prevHs * prevMatches) + (newAgg.headshotPct() * newMatches)) / totalMatches;
            double weightedAcs = ((prevAcs * prevMatches) + (newAgg.avgCombatScore() * newMatches)) / totalMatches;

            return new CombatAggregate(
                    totalMatches,
                    (existing.getWins() != null ? existing.getWins() : 0) + newAgg.wins(),
                    weightedKd,
                    weightedHs,
                    weightedAcs,
                    !newAgg.topAgentsJson().equals("[]") ? newAgg.topAgentsJson() : existing.getTopAgentsJson(),
                    allIds
            );
        }

        // 3. Sin partidas nuevas procesadas, conservar los datos anteriores
        return new CombatAggregate(
                existing.getMatchesPlayed() != null ? existing.getMatchesPlayed() : 0,
                existing.getWins() != null ? existing.getWins() : 0,
                existing.getKdRatio() != null ? existing.getKdRatio() : 0.0,
                existing.getHeadshotPct() != null ? existing.getHeadshotPct() : 0.0,
                existing.getAvgCombatScore() != null ? existing.getAvgCombatScore() : 0.0,
                existing.getTopAgentsJson() != null ? existing.getTopAgentsJson() : "[]",
                allIds
        );
    }

    // ── Persistencia ───────────────────────────────────────────────────────────
    private PlayerStats saveStatsKeepingCombat(PlayerStats existing, User user,
                                                String tier, int rr, int peakTier,
                                                int matches, Integer wins, Integer losses) {
        PlayerStats stats = existing != null ? existing
                : PlayerStats.builder().user(user).build();
        stats.setCurrentTier(tier);
        stats.setRankingInTier(rr);
        stats.setPeakTier(peakTier);
        stats.setMatchesPlayed(matches);
        if (wins != null)   stats.setWins(wins);
        if (losses != null) stats.setLosses(losses);
        if (matches > 0 && wins != null)
            stats.setWinratePct(wins * 100.0 / matches);
        stats.setLastUpdated(Instant.now());
        return playerStatsRepository.save(stats);
    }

    private PlayerStats saveStats(PlayerStats existing, User user, String tier, int rr, int peakTier,
                                   int matches, int wins, int losses,
                                   double kd, double hsPct, double acs,
                                   String topAgentsJson, String processedMatchIdsJson) {
        PlayerStats stats = existing != null ? existing
                : PlayerStats.builder().user(user).build();
        stats.setCurrentTier(tier);
        stats.setRankingInTier(rr);
        stats.setPeakTier(peakTier);
        stats.setMatchesPlayed(matches);
        stats.setWins(wins);
        stats.setLosses(losses);
        stats.setWinratePct(matches == 0 ? 0.0 : (wins * 100.0 / matches));
        stats.setKdRatio(kd);
        stats.setHeadshotPct(hsPct);
        stats.setAvgCombatScore(acs);
        stats.setTopAgentsJson(topAgentsJson);
        stats.setProcessedMatchIdsJson(processedMatchIdsJson);
        stats.setLastUpdated(Instant.now());
        return playerStatsRepository.save(stats);
    }

    // ── Utilidades de IDs procesados ──────────────────────────────────────────
    private Set<String> loadProcessedMatchIds(PlayerStats existing) {
        if (existing == null || existing.getProcessedMatchIdsJson() == null
                || existing.getProcessedMatchIdsJson().isBlank()) {
            return new HashSet<>();
        }
        try {
            List<String> list = objectMapper.readValue(
                    existing.getProcessedMatchIdsJson(), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            log.warn("  No se pudo deserializar processedMatchIdsJson: {}", e.getMessage());
            return new HashSet<>();
        }
    }

    private String serializeProcessedIds(Set<String> ids) {
        // Limitar a maxProcessedMatchIds para no crecer indefinidamente
        List<String> list = new ArrayList<>(ids);
        if (list.size() > maxProcessedMatchIds) {
            list = list.subList(list.size() - maxProcessedMatchIds, list.size());
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.error("Error serializando processedMatchIds", e);
            return "[]";
        }
    }

    // ── Construccion de agregado ───────────────────────────────────────────────
    private CombatAggregate buildAggregate(int matchCount, int wins,
                                            double kills, double deaths,
                                            double headshots, double bodyshots, double legshots,
                                            double acsSum,
                                            Map<String, AgentTracker> agentMap,
                                            Set<String> processedIds) {
        double kd    = deaths == 0 ? kills : kills / deaths;
        double total = headshots + bodyshots + legshots;
        double hsPct = total == 0 ? 0.0 : (headshots * 100.0 / total);
        double avg   = matchCount == 0 ? 0.0 : acsSum / matchCount;

        String topAgentsJson = "[]";
        try {
            List<Map<String, Object>> topAgents = agentMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().matches, a.getValue().matches))
                .limit(3)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", e.getKey());
                    map.put("matches", e.getValue().matches);
                    map.put("winrate", e.getValue().matches == 0 ? 0.0 : (e.getValue().wins * 100.0 / e.getValue().matches));
                    double agentKd = e.getValue().deaths == 0 ? e.getValue().kills
                            : (double) e.getValue().kills / e.getValue().deaths;
                    map.put("kd", agentKd);
                    return map;
                })
                .collect(Collectors.toList());
            topAgentsJson = objectMapper.writeValueAsString(topAgents);
        } catch (Exception ex) {
            log.error("Error serializando top agents", ex);
        }

        return new CombatAggregate(matchCount, wins, kd, hsPct, avg, topAgentsJson, processedIds);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ── Records y clases internas ─────────────────────────────────────────────
    record CombatAggregate(int matches, int wins, double kd, double headshotPct,
                           double avgCombatScore, String topAgentsJson, Set<String> processedMatchIds) {}

    private static class AgentTracker {
        int matches, wins, kills, deaths;
    }
}
