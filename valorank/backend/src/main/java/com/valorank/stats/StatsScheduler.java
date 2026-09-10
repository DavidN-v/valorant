package com.valorank.stats;

import com.valorank.integration.henrik.HenrikDevClient;
import com.valorank.user.User;
import com.valorank.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler automatico de actualizacion de stats.
 * Obtiene el acto activo UNA sola vez y luego procesa a todos los jugadores
 * con pausa entre cada uno para respetar el rate limit de HenrikDev.
 *
 * Frecuencia configurable en application.yml -> scheduler.stats-refresh-rate-ms
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatsScheduler {

    private final UserRepository userRepository;
    private final StatsUpdateService statsUpdateService;
    private final PremierStatsUpdateService premierStatsUpdateService;

    @Scheduled(fixedDelayString = "${scheduler.stats-refresh-rate-ms}",
               initialDelayString = "${scheduler.stats-refresh-rate-ms}")
    public void refreshAllStats() {
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            log.info("Scheduler: no hay usuarios registrados, nada que actualizar.");
            return;
        }

        log.info("=== [SCHEDULER] Actualizando stats competitivas y premier de {} usuarios ===", users.size());

        // Obtener el acto activo UNA sola vez para aplicarlo a todos (para competitivo)
        HenrikDevClient.ActInfo actInfo;
        try {
            actInfo = statsUpdateService.getCurrentActInfo();
            if (actInfo != null) {
                log.info("  Acto activo (Competitivo): {}", actInfo.shortId());
            } else {
                log.warn("  No se pudo obtener el acto activo. Cada jugador usara su propio fallback.");
            }
        } catch (Exception e) {
            log.warn("  Error obteniendo acto activo en scheduler: {}", e.getMessage());
            actInfo = null;
        }

        int okComp = 0, failComp = 0;
        int okPrem = 0, failPrem = 0;
        
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            
            // 1. Competitivo
            try {
                statsUpdateService.refreshStatsFor(user, actInfo);
                okComp++;
            } catch (Exception e) {
                log.warn("  [COMPETITIVO OMITIDO TEMPORALMENTE] {}: {}", user.getRiotId(), e.getMessage());
                failComp++;
            }
            
            // Pausa entre requests de competitivo y premier
            try { Thread.sleep(3500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            
            // 2. Premier
            try {
                premierStatsUpdateService.refreshPremierStatsFor(user);
                okPrem++;
            } catch (Exception e) {
                log.warn("  [PREMIER OMITIDO TEMPORALMENTE] {}: {}", user.getRiotId(), e.getMessage());
                failPrem++;
            }

            if (i < users.size() - 1) {
                try { Thread.sleep(4000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }

        log.info("=== [SCHEDULER TERMINADO] Comp: {} OK, {} fallos | Premier: {} OK, {} fallos ===", 
                 okComp, failComp, okPrem, failPrem);
    }
}
