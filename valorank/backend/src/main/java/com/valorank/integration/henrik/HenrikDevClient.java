package com.valorank.integration.henrik;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente para la API publica no-oficial de Valorant: https://docs.henrikdev.xyz
 * Necesitas una API key gratuita (se pide en su Discord) y ponerla en application.yml.
 *
 * Usa java.net.http.HttpClient (Java 11+) en lugar de RestTemplate para evitar
 * el fallo de resolucion DNS de la JVM en Windows (UnknownHostException).
 */
@Component
@Slf4j
public class HenrikDevClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${henrikdev.base-url}")
    private String baseUrl;

    @Value("${henrikdev.api-key}")
    private String apiKey;

    private volatile ActInfo cachedActInfo = null;
    private volatile java.time.Instant actInfoExpiry = java.time.Instant.MIN;

    /**
     * Devuelve el JSON crudo de MMR/rank actual del jugador.
     * Endpoint: GET /valorant/v2/mmr/{region}/{name}/{tag}
     */
    public JsonNode getMmr(String region, String name, String tag) {
        String url = String.format("%s/valorant/v2/mmr/%s/%s/%s",
                baseUrl, region, urlEncode(name), urlEncode(tag));
        log.info("Consultando MMR: {}", url);
        return get(url);
    }

    /**
     * Contiene tanto el UUID del acto como el ID corto (eXXaX) para usar en la API de lifetime.
     */
    public record ActInfo(String uuid, String shortId) {}

    /**
     * Devuelve el acto activo con UUID y el short ID (eXXaX) necesario para /v1/lifetime/matches.
     * Endpoint: GET /valorant/v1/content
     * Cuenta el numero de episodio y de acto para construir el shortId (ej: Episode 11, Act 5 = "e11a5").
     * Incluye cache en memoria de 2 horas para no agotar el rate limit de HenrikDev innecesariamente.
     */
    public ActInfo getCurrentActInfo() {
        if (cachedActInfo != null && java.time.Instant.now().isBefore(actInfoExpiry)) {
            return cachedActInfo;
        }

        String url = baseUrl + "/valorant/v1/content";
        log.info("Obteniendo informacion del acto activo (cache miss): {}", url);
        try {
            JsonNode root = get(url);
            JsonNode acts = root.path("data").path("acts");

            // Paso 1: construir mapa episodeId -> numero de episodio (en orden cronologico)
            // Los episodios tienen type="episode", los actos tienen type="act"
            // La API los devuelve con los mas recientes PRIMERO, asi que hay que reordenarlos
            java.util.List<JsonNode> allEntries = new java.util.ArrayList<>();
            for (JsonNode a : acts) allEntries.add(a);
            // Revertir para tener el orden cronologico (mas antiguos primero)
            java.util.Collections.reverse(allEntries);

            // Paso 2: contar episodios y actos en orden cronologico
            java.util.Map<String, Integer> episodeIdToNum = new java.util.LinkedHashMap<>();
            int episodeNum = 0;
            for (JsonNode a : allEntries) {
                String type = a.path("type").asText("");
                if ("episode".equalsIgnoreCase(type)) {
                    episodeNum++;
                    episodeIdToNum.put(a.path("id").asText(""), episodeNum);
                }
            }

            // Paso 3: encontrar el acto activo y calcular su shortId
            java.util.Map<String, Integer> episodeActCount = new java.util.LinkedHashMap<>();
            for (JsonNode a : allEntries) {
                String type = a.path("type").asText("");
                String name = a.path("name").asText("");
                boolean isActive = a.path("isActive").asBoolean(false);
                String id = a.path("id").asText("");
                String parentId = a.path("parentId").asText("");

                boolean isAct = "act".equalsIgnoreCase(type) || name.toUpperCase().contains("ACT");
                if (isAct) {
                    // Usar el parentId para saber a que episodio pertenece este acto
                    Integer epNum = episodeIdToNum.get(parentId);
                    if (epNum == null) epNum = episodeNum; // fallback al ultimo episodio
                    int actCount = episodeActCount.getOrDefault(parentId, 0) + 1;
                    episodeActCount.put(parentId, actCount);

                    if (isActive) {
                        String shortId = "e" + epNum + "a" + actCount;
                        log.info("Acto activo detectado: {} | UUID={} | shortId={} (ep={}, act={})", name, id, shortId, epNum, actCount);
                        ActInfo info = new ActInfo(id, shortId);
                        cachedActInfo = info;
                        actInfoExpiry = java.time.Instant.now().plus(Duration.ofHours(2));
                        return info;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el acto activo: {}", e.getMessage());
            if (cachedActInfo != null) return cachedActInfo; // fallback a valor anterior
        }
        return null;
    }

    /**
     * Devuelve el UUID del acto activo (shortcut para compatibilidad con codigo existente).
     */
    public String getCurrentSeasonId() {
        ActInfo info = getCurrentActInfo();
        return info != null ? info.uuid() : null;
    }

    /**
     * Obtiene TODAS las partidas competitivas del jugador en el acto actual usando el endpoint
     * /v1/lifetime/matches con paginacion. Combina todas las paginas en un ArrayNode.
     * Endpoint: GET /valorant/v1/lifetime/matches/{region}/{name}/{tag}?mode=competitive&season={shortId}&page=N
     */
    public JsonNode getAllLifetimeMatches(String region, String name, String tag, String seasonShortId) {
        return getAllLifetimeMatches(region, name, tag, seasonShortId, "competitive");
    }

    public JsonNode getAllLifetimeMatches(String region, String name, String tag, String seasonShortId, String mode) {
        List<JsonNode> allMatches = new ArrayList<>();
        int page = 1;
        int maxPages = 20; // limite de seguridad para no hacer peticiones infinitas

        log.info("Descargando TODAS las partidas de {}/{} en acto={} modo={}", name, tag, seasonShortId, mode);

        while (page <= maxPages) {
            String seasonParam = (seasonShortId != null && !seasonShortId.isBlank()) ? ("&season=" + seasonShortId) : "";
            String url = String.format("%s/valorant/v1/lifetime/matches/%s/%s/%s?mode=%s%s&page=%d&size=20",
                    baseUrl, region, urlEncode(name), urlEncode(tag), mode, seasonParam, page);
            log.info("Pagina {} partidas: {}", page, url);

            JsonNode response = null;
            int retries = 0;
            int maxRetries = 3;

            while (retries < maxRetries) {
                try {
                    response = get(url);
                    break;
                } catch (RuntimeException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                        retries++;
                        long waitMs = 6000L * retries;
                        log.warn("Rate limit en pagina {} (intento {}/{}). Esperando {}ms...", page, retries, maxRetries, waitMs);
                        try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    } else {
                        log.warn("Error en pagina {} de lifetime/matches: {}", page, e.getMessage());
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Error inesperado en pagina {} de lifetime/matches: {}", page, e.getMessage());
                    break;
                }
            }

            if (response == null) {
                if (page == 1) {
                    log.warn("Fallo total al descargar pagina 1 para {}/{}. Retornando null.", name, tag);
                    return null;
                }
                break;
            }

            JsonNode data = response.path("data");
            if (!data.isArray() || data.size() == 0) {
                log.info("No hay mas partidas en pagina {}, total={}", page, allMatches.size());
                break;
            }

            for (JsonNode m : data) {
                allMatches.add(m);
            }
            log.info("Pagina {} -> {} partidas (total acumulado: {})", page, data.size(), allMatches.size());

            // Si la pagina tiene menos de 20 elementos, es la ultima
            if (data.size() < 20) {
                break;
            }

            page++;
            // Pausa entre paginas para evitar rate limit
            try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }

        ArrayNode result = mapper.createArrayNode();
        allMatches.forEach(result::add);
        return result;
    }

    /**
     * Obtiene los IDs exactos de las partidas del jugador en un acto especifico
     * usando el endpoint /v1/lifetime/mmr-history que incluye season info por match.
     * Retorna lista de match IDs filtrados al acto indicado.
     */
    public List<String> getSeasonMatchIds(String region, String name, String tag, String seasonShortId) {
        List<String> matchIds = new ArrayList<>();
        int page = 1;
        int maxPages = 15;

        log.info("Obteniendo match IDs exactos de {}/{} para acto={}", name, tag, seasonShortId);

        while (page <= maxPages) {
            String url = String.format("%s/valorant/v1/lifetime/mmr-history/%s/%s/%s?page=%d&size=20",
                    baseUrl, region, urlEncode(name), urlEncode(tag), page);
            try {
                JsonNode response = get(url);
                JsonNode data = response.path("data");
                if (!data.isArray() || data.size() == 0) break;

                for (JsonNode entry : data) {
                    String matchSeason = entry.path("season").path("short").asText("");
                    if (seasonShortId.equals(matchSeason)) {
                        String matchId = entry.path("match_id").asText("");
                        if (!matchId.isBlank()) matchIds.add(matchId);
                    } else if (!matchIds.isEmpty()) {
                        // Si ya encontramos partidas del acto y ahora hay de otro acto anterior,
                        // estamos fuera del rango â€” parar
                        log.info("  Match ID fuera del acto ({}) -> parando paginacion", matchSeason);
                        return matchIds;
                    }
                }

                if (data.size() < 20) break;
                page++;
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }

            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                    log.warn("Rate limit en mmr-history pagina {}. Esperando 8s...", page);
                    try { Thread.sleep(8000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.warn("Error en mmr-history pagina {}: {}", page, e.getMessage());
                    break;
                }
            } catch (Exception e) {
                log.warn("Error inesperado en mmr-history: {}", e.getMessage());
                break;
            }
        }

        log.info("  Total match IDs encontrados para {}: {}", seasonShortId, matchIds.size());
        return matchIds;
    }

    /**
     * Obtiene los detalles completos de una partida por su ID.
     * Endpoint: GET /valorant/v2/match/{matchId}
     * Incluye metadata.rounds_played (EXACTO) y stats completos de cada jugador.
     */
    public JsonNode getMatchById(String matchId) {
        String url = String.format("%s/valorant/v2/match/%s", baseUrl, matchId);
        log.debug("Consultando match {}", matchId);
        return get(url);
    }

    /**
     * Devuelve el JSON crudo con el historial de partidas recientes (fallback).
     * Endpoint: GET /valorant/v3/matches/{region}/{name}/{tag}
     */
    public JsonNode getRecentMatches(String region, String name, String tag) {
        String url = String.format("%s/valorant/v3/matches/%s/%s/%s?mode=competitive&size=20",
                baseUrl, region, urlEncode(name), urlEncode(tag));
        log.info("Consultando partidas recientes (fallback): {}", url);
        return get(url);
    }

    private JsonNode get(String url) {
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", apiKey)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                int httpStatus = response.statusCode();

                if (httpStatus == 404) {
                    throw new RuntimeException("Jugador no encontrado. Verifica que tu Riot ID (nombre#tag) sea correcto.");
                }
                if (httpStatus == 401 || httpStatus == 403) {
                    throw new RuntimeException("API key de HenrikDev invalida o expirada.");
                }
                if (httpStatus == 429) {
                    throw new RuntimeException("Rate limit de HenrikDev alcanzado. Espera un momento antes de volver a intentarlo.");
                }
                if (httpStatus >= 500) {
                    throw new RuntimeException("Error del servidor de HenrikDev (" + httpStatus + "). Intenta mas tarde.");
                }

                JsonNode root = mapper.readTree(body);

                // HenrikDev devuelve status en el JSON incluso en respuestas 200
                int jsonStatus = root.path("status").asInt(httpStatus);
                if (jsonStatus >= 400) {
                    String msg = root.path("errors").path(0).path("message").asText(
                                 root.path("message").asText("Jugador no encontrado en la API de Valorant"));
                    log.warn("HenrikDev devolvio error JSON {}: {}", jsonStatus, msg);
                    throw new RuntimeException(msg);
                }

                return root;

            } catch (RuntimeException e) {
                // Errores de negocio (rate limit, 404, etc.) â†’ no reintentar
                throw e;
            } catch (Exception e) {
                // Error de red (DNS, conexion rechazada, timeout) â†’ reintentar
                boolean isNetworkError = e instanceof java.net.ConnectException
                        || e.getCause() instanceof java.nio.channels.UnresolvedAddressException
                        || e.getCause() instanceof java.net.ConnectException
                        || (e.getMessage() != null && e.getMessage().contains("null"));
                if (isNetworkError && attempt < maxAttempts) {
                    log.warn("Error de red en intento {}/{} para {}: {}. Reintentando en 5s...",
                            attempt, maxAttempts, url, e.getClass().getSimpleName());
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else if (attempt < maxAttempts) {
                    log.warn("Error en intento {}/{} para {}: {}. Reintentando en 3s...",
                            attempt, maxAttempts, url, e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.error("Error consultando HenrikDev URL={} despues de {} intentos: {}", url, maxAttempts, e.getMessage());
                    throw new RuntimeException("Error consultando la API de Valorant: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("Error inesperado en get() - no deberia llegar aqui");
    }

    private String urlEncode(String value) {
        // Usa %20 para espacios (URL encoding, no form encoding con +)
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

