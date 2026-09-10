package com.valorank.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Servicio para envio de notificaciones a Discord mediante Webhooks.
 * Notifica subidas/bajadas de rango y eventos de leaderboard.
 */
@Service
@Slf4j
public class DiscordWebhookService {

    @Value("${discord.webhook-url:}")
    private String webhookUrl;

    @Value("${discord.enabled:true}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String VALORANT_TIER_UUID = "03621f52-342b-cf4e-4f86-9350a49c6d04";

    /**
     * Envia una notificacion cuando un jugador sube o baja de rango.
     */
    public void sendRankChangeNotification(String riotId, String oldRank, String newRank,
                                           int newTierNumber, int newRr,
                                           Double kd, Double hsPct, Double winrate,
                                           Integer matches, Integer wins, Integer losses) {
        if (!isEnabled()) {
            return;
        }

        try {
            int oldTierNum = tierNameToNumber(oldRank);
            boolean isRankUp = newTierNumber > oldTierNum;

            String title = isRankUp
                    ? "🎉 ¡SUBIDA DE RANGO DETECTADA!"
                    : "📉 ¡DESCENSO DE RANGO DETECTADA!";

            // Verde/Dorado para subida, Rojo para bajada
            int color = isRankUp ? 0x10B981 : 0xEF4444;

            String iconUrl = String.format("https://media.valorant-api.com/competitivetiers/%s/%d/largeicon.png",
                    VALORANT_TIER_UUID, Math.max(0, newTierNumber));

            String formattedOldRank = (oldRank == null || oldRank.isBlank() || oldRank.equalsIgnoreCase("unrated"))
                    ? "Sin Rango" : oldRank;
            String formattedNewRank = (newRank == null || newRank.isBlank() || newRank.equalsIgnoreCase("unrated"))
                    ? "Sin Rango" : newRank;

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(Map.of(
                    "name", "👤 Jugador",
                    "value", "`" + riotId + "`",
                    "inline", true
            ));
            fields.add(Map.of(
                    "name", isRankUp ? "📈 Progreso" : "📉 Nuevo Rango",
                    "value", String.format("**%s** ➔ **%s** (%d RR)", formattedOldRank, formattedNewRank, newRr),
                    "inline", false
            ));

            String statsSummary = String.format("• **K/D:** `%.2f`\n• **HS%%:** `%.1f%%`\n• **Winrate:** `%.1f%%` (%dV - %dD)\n• **Partidas:** `%d`",
                    kd != null ? kd : 0.0,
                    hsPct != null ? hsPct : 0.0,
                    winrate != null ? winrate : 0.0,
                    wins != null ? wins : 0,
                    losses != null ? losses : 0,
                    matches != null ? matches : 0);

            fields.add(Map.of(
                    "name", "🎯 Estadísticas del Acto",
                    "value", statsSummary,
                    "inline", false
            ));

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", title);
            embed.put("description", isRankUp
                    ? String.format("¡Felicidades a **%s** por alcanzar **%s**! 🔥", riotId, formattedNewRank)
                    : String.format("F en el chat por **%s**, ha caído a **%s**. Toca recuperar ese elo. 💀", riotId, formattedNewRank));
            embed.put("color", color);
            embed.put("fields", fields);
            embed.put("thumbnail", Map.of("url", iconUrl));
            embed.put("footer", Map.of(
                    "text", "VALORANT • Tracker de Amigos",
                    "icon_url", "https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/27/largeicon.png"
            ));
            embed.put("timestamp", Instant.now().toString());

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "VALORANT BOT");
            payload.put("avatar_url", "https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/27/largeicon.png");
            payload.put("embeds", List.of(embed));

            postToWebhook(payload);
            log.info("[DISCORD WEBHOOK] Notificación enviada con éxito para '{}': {} -> {}", riotId, oldRank, newRank);
        } catch (Exception e) {
            log.warn("[DISCORD WEBHOOK] Error al enviar notificación de cambio de rango: {}", e.getMessage());
        }
    }

    /**
     * Envia un mensaje de prueba para verificar conexion inmediata con Discord.
     */
    public boolean sendTestNotification() {
        if (!isEnabled()) {
            log.warn("[DISCORD WEBHOOK] Webhook deshabilitado o URL no configurada.");
            return false;
        }

        try {
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "⚡ ¡VALORANT BOT CONECTADO!");
            embed.put("description", "¡La integración con Discord está activa y funcionando correctamente!\n\nCada vez que alguien del grupo suba o baje de rango en Competitivo, recibirás una alerta automática aquí.");
            embed.put("color", 0x38BDF8); // Cyan
            embed.put("thumbnail", Map.of("url", "https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/27/largeicon.png"));
            embed.put("fields", List.of(
                    Map.of("name", "🛡️ Servidor Conectado", "value", "Spring Boot Backend (Valorant)", "inline", true),
                    Map.of("name", "📡 Estado", "value", "`ONLINE 🟢`", "inline", true)
            ));
            embed.put("footer", Map.of("text", "VALORANT • Tracker de Amigos"));
            embed.put("timestamp", Instant.now().toString());

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "VALORANT BOT");
            payload.put("avatar_url", "https://media.valorant-api.com/competitivetiers/03621f52-342b-cf4e-4f86-9350a49c6d04/27/largeicon.png");
            payload.put("embeds", List.of(embed));

            postToWebhook(payload);
            log.info("[DISCORD WEBHOOK] Notificación de prueba enviada exitosamente.");
            return true;
        } catch (Exception e) {
            log.error("[DISCORD WEBHOOK] Error enviando mensaje de prueba a Discord", e);
            return false;
        }
    }

    private void postToWebhook(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(webhookUrl, request, String.class);
    }

    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    public static int tierNameToNumber(String tier) {
        if (tier == null || tier.isBlank()) return 0;
        String t = tier.toLowerCase().trim();
        if (t.startsWith("iron 1"))       return 3;
        if (t.startsWith("iron 2"))       return 4;
        if (t.startsWith("iron 3"))       return 5;
        if (t.startsWith("bronze 1"))     return 6;
        if (t.startsWith("bronze 2"))     return 7;
        if (t.startsWith("bronze 3"))     return 8;
        if (t.startsWith("silver 1"))     return 9;
        if (t.startsWith("silver 2"))     return 10;
        if (t.startsWith("silver 3"))     return 11;
        if (t.startsWith("gold 1"))       return 12;
        if (t.startsWith("gold 2"))       return 13;
        if (t.startsWith("gold 3"))       return 14;
        if (t.startsWith("platinum 1"))   return 15;
        if (t.startsWith("platinum 2"))   return 16;
        if (t.startsWith("platinum 3"))   return 17;
        if (t.startsWith("diamond 1"))    return 18;
        if (t.startsWith("diamond 2"))    return 19;
        if (t.startsWith("diamond 3"))    return 20;
        if (t.startsWith("ascendant 1"))  return 21;
        if (t.startsWith("ascendant 2"))  return 22;
        if (t.startsWith("ascendant 3"))  return 23;
        if (t.startsWith("immortal 1"))   return 24;
        if (t.startsWith("immortal 2"))   return 25;
        if (t.startsWith("immortal 3"))   return 26;
        if (t.startsWith("radiant"))      return 27;
        return 0;
    }
}
