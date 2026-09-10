package com.valorank.stats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.valorank.integration.henrik.HenrikDevClient;
import com.valorank.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PremierStatsUpdateService {

    private final HenrikDevClient henrikDevClient;
    private final PremierStatsRepository premierStatsRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void refreshPremierStatsFor(User user) {
        String[] parts = user.getRiotId().split("#");
        if (parts.length != 2) return;
        String name = parts[0];
        String tag = parts[1];

        log.info("[PREMIER INICIADO] '{}#{}' region={}", name, tag, user.getRegion());

        PremierStats existing = premierStatsRepository.findByUser(user).orElse(null);

        // En Premier vamos a pedir siempre todas las partidas sin filtro de temporada (lifetime=Premier)
        // Opcional: Podriamos filtrar por el season activo, pero Tracker.gg muestra el lifetime premier
        // O podriamos sacar el season de las partidas. Por ahora pedimos modo=premier sin season.
        JsonNode allPremierMatches = henrikDevClient.getAllLifetimeMatches(user.getRegion(), name, tag, "", "premier");

        if (allPremierMatches == null) {
            log.warn("  [ERROR API PREMIER] Falló la conexión con HenrikDev para {}. Conservando stats existentes sin modificar.", user.getRiotId());
            return;
        }

        if (!allPremierMatches.isArray() || allPremierMatches.isEmpty()) {
            if (existing != null && existing.getMatchesPlayed() != null && existing.getMatchesPlayed() > 0) {
                log.warn("  [PREMIER CONSERVADO] HenrikDev devolvió 0 partidas pero {} ya tiene {} registradas. No se sobrescribe.",
                        user.getRiotId(), existing.getMatchesPlayed());
                return;
            }
            log.info("  [NUEVO JUGADOR SIN PARTIDAS PREMIER] Guardando stats iniciales en 0.");
            saveZeroStats(user, existing);
            return;
        }

        Set<String> alreadyProcessed = new HashSet<>();
        if (existing != null && existing.getProcessedMatchIdsJson() != null) {
            try {
                List<String> list = MAPPER.readValue(existing.getProcessedMatchIdsJson(), new TypeReference<>() {});
                alreadyProcessed.addAll(list);
            } catch (Exception ignored) {}
        }

        // Determinar cuantos matches ya procesamos y cuantos nuevos hay
        List<JsonNode> matchesToProcess = new ArrayList<>();
        List<String> newMatchIds = new ArrayList<>();

        for (JsonNode m : allPremierMatches) {
            String matchId = m.path("meta").path("id").asText();
            if (!alreadyProcessed.contains(matchId)) {
                matchesToProcess.add(m);
                newMatchIds.add(matchId);
            }
        }

        // Si ya estan procesadas y el jugador SI tiene partidas registradas (>0), omitir
        if (matchesToProcess.isEmpty() && existing != null && existing.getMatchesPlayed() != null && existing.getMatchesPlayed() > 0) {
            log.info("  [SIN CAMBIOS PREMIER] {} partidas ya procesadas.", alreadyProcessed.size());
            return;
        }

        log.info("  [PROCESANDO PREMIER] Recalculando con {} partidas de HenrikDev.", allPremierMatches.size());

        // Recuperar stats previas
        int totalMatches = existing != null && existing.getMatchesPlayed() != null ? existing.getMatchesPlayed() : 0;
        int totalWins = existing != null && existing.getWins() != null ? existing.getWins() : 0;
        int totalLosses = existing != null && existing.getLosses() != null ? existing.getLosses() : 0;
        double currentAcsTotal = (existing != null && existing.getAvgCombatScore() != null) ? (existing.getAvgCombatScore() * totalMatches) : 0;

        // Recuperar sumas previas para ratios (calculado inverso, no exacto pero sirve si no guardamos los raw numbers)
        // Lo ideal es recalcular desde cero leyendo TODO de `allPremierMatches` en lugar de acumular.
        // Dado que getAllLifetimeMatches nos da hasta 400 partidas, podemos simplemente recalcular TODO
        // para tener precision exacta en HS% y KD.
        
        recalculateFromScratch(user, existing, allPremierMatches);
    }

    private void recalculateFromScratch(User user, PremierStats existing, JsonNode allPremierMatches) {
        int matches = 0;
        int wins = 0;
        int losses = 0;
        int kills = 0;
        int deaths = 0;
        int headshots = 0;
        int bodyshots = 0;
        int legshots = 0;
        double totalAcs = 0;

        Map<String, AgentStats> agentStatsMap = new HashMap<>();
        List<String> processedIds = new ArrayList<>();

        for (JsonNode m : allPremierMatches) {
            String matchId = m.path("meta").path("id").asText();
            processedIds.add(matchId);

            JsonNode s = m.path("stats");
            kills += s.path("kills").asInt(0);
            deaths += s.path("deaths").asInt(0);
            headshots += s.path("shots").path("head").asInt(0);
            bodyshots += s.path("shots").path("body").asInt(0);
            legshots += s.path("shots").path("leg").asInt(0);
            
            // ACS = score / rounds played. 
            // En la API lifetime, a veces viene score pero no las rondas jugadas directamente en 'stats'.
            // teams.red y teams.blue nos dan las rondas.
            int rounds = m.path("teams").path("red").asInt(0) + m.path("teams").path("blue").asInt(0);
            int score = s.path("score").asInt(0);
            if (rounds > 0) {
                totalAcs += ((double) score / rounds);
            }

            // Win/Loss
            String myTeam = s.path("team").asText("").toLowerCase();
            int myTeamScore = m.path("teams").path(myTeam).asInt(0);
            int enemyTeamScore = m.path("teams").path(myTeam.equals("red") ? "blue" : "red").asInt(0);

            boolean won = myTeamScore > enemyTeamScore;
            if (won) wins++; else losses++;

            // Agentes
            String agentName = s.path("character").path("name").asText("Unknown");
            AgentStats as = agentStatsMap.computeIfAbsent(agentName, k -> new AgentStats(agentName));
            as.matches++;
            if (won) as.wins++;
            as.kills += s.path("kills").asInt(0);
            as.deaths += s.path("deaths").asInt(0);
            
            matches++;
        }

        PremierStats ps = existing != null ? existing : new PremierStats();
        ps.setUser(user);
        ps.setCurrentTier("Premier");
        ps.setPeakTier(0);
        ps.setRankingInTier(0);
        
        ps.setMatchesPlayed(matches);
        ps.setWins(wins);
        ps.setLosses(losses);

        double winrate = matches > 0 ? ((double) wins / matches) * 100.0 : 0.0;
        double kd = deaths > 0 ? (double) kills / deaths : (double) kills;
        int totalShots = headshots + bodyshots + legshots;
        double hsPct = totalShots > 0 ? ((double) headshots / totalShots) * 100.0 : 0.0;
        double acs = matches > 0 ? (totalAcs / matches) : 0.0;

        ps.setWinratePct(winrate);
        ps.setKdRatio(kd);
        ps.setHeadshotPct(hsPct);
        ps.setAvgCombatScore(acs);

        List<StatsDtos.AgentStatResponse> topAgents = agentStatsMap.values().stream()
                .map(a -> new StatsDtos.AgentStatResponse(
                        a.name,
                        a.matches,
                        ((double) a.wins / a.matches) * 100.0,
                        a.deaths > 0 ? (double) a.kills / a.deaths : (double) a.kills
                ))
                .sorted(Comparator.comparing(StatsDtos.AgentStatResponse::matches).reversed())
                .toList();

        try {
            ps.setTopAgentsJson(MAPPER.writeValueAsString(topAgents));
            ps.setProcessedMatchIdsJson(MAPPER.writeValueAsString(processedIds));
        } catch (Exception e) {
            log.error("Error serializando JSON Premier", e);
        }

        premierStatsRepository.save(ps);
        log.info("  [PREMIER OK] KD={} | HS%={} | ACS={} | Partidas={}",
                String.format("%.2f", kd), String.format("%.1f", hsPct), String.format("%.1f", acs), matches);
    }

    private void saveZeroStats(User user, PremierStats existing) {
        PremierStats ps = existing != null ? existing : new PremierStats();
        ps.setUser(user);
        ps.setCurrentTier("Premier");
        ps.setMatchesPlayed(0);
        ps.setWins(0);
        ps.setLosses(0);
        ps.setWinratePct(0.0);
        ps.setKdRatio(0.0);
        ps.setHeadshotPct(0.0);
        ps.setAvgCombatScore(0.0);
        ps.setTopAgentsJson("[]");
        ps.setProcessedMatchIdsJson("[]");
        premierStatsRepository.save(ps);
    }

    private static class AgentStats {
        String name;
        int matches;
        int wins;
        int kills;
        int deaths;
        public AgentStats(String name) { this.name = name; }
    }
}
