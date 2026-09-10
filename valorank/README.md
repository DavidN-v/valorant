# ValoRank — App de stats de Valorant para tu grupo de amigos

## Estructura del monorepo
```
valorank/
├── backend/    → API Spring Boot
└── frontend/   → App Angular
```

## ¿Qué hace?
- Cada amigo se registra vinculando su **Riot ID** (`nombre#tag`).
- El backend consulta la **API pública no-oficial de HenrikDev** (https://docs.henrikdev.xyz) para traer rango, winrate, K/D, headshot% y ACS.
- Un **scheduler** actualiza automáticamente las stats de todos cada 15 minutos (configurable).
- Un endpoint **público** de leaderboard muestra a todos ordenados por la stat que elijas (rango, winrate, K/D, etc.), sin necesidad de login.
- Cada usuario logueado ve su propio dashboard con sus stats.

## 1. Consigue tu API Key de HenrikDev
Es gratis. Entra a su Discord (link en https://docs.henrikdev.xyz) y sigue las instrucciones para pedir una key. Sin esto, las consultas a la API fallarán.

## 2. Backend (Spring Boot)

### Requisitos
- Java 21
- Maven
- PostgreSQL corriendo localmente

### Configurar
1. Crea la base de datos:
   ```sql
   CREATE DATABASE valorank;
   CREATE USER valorank_user WITH PASSWORD 'valorank_pass';
   GRANT ALL PRIVILEGES ON DATABASE valorank TO valorank_user;
   ```
2. Edita `backend/src/main/resources/application.yml`:
   - `henrikdev.api-key`: pega tu API key.
   - `app.jwt.secret`: cambia por una clave secreta larga (mínimo 32 caracteres).
   - Ajusta usuario/contraseña de Postgres si es distinto.

### Levantar
```bash
cd backend
mvn spring-boot:run
```
Queda corriendo en `http://localhost:8080`.

## 3. Frontend (Angular)

### Requisitos
- Node.js 18+
- Angular CLI (`npm install -g @angular/cli`)

### Levantar
```bash
cd frontend
npm install
npm start
```
Queda corriendo en `http://localhost:4200`.

## Endpoints principales
| Método | Ruta | Público | Descripción |
|---|---|---|---|
| POST | `/auth/register` | Sí | Crea cuenta y vincula Riot ID |
| POST | `/auth/login` | Sí | Login, devuelve JWT |
| GET | `/me/stats` | No (requiere JWT) | Mis stats |
| POST | `/me/stats/refresh` | No (requiere JWT) | Fuerza actualización de mis stats |
| GET | `/leaderboard?sortBy=rr\|winrate\|kd\|headshot\|acs\|matches` | Sí | Ranking de todos los amigos |

## Notas y siguientes pasos sugeridos
- La API de HenrikDev tiene **rate limits** (varía según el tier de tu key). Si tienes muchos amigos, sube el intervalo del scheduler en `application.yml` (`scheduler.stats-refresh-rate-ms`).
- El campo `region` debe ser uno de: `na`, `eu`, `latam`, `br`, `ap`, `kr`.
- Para producción: mover secretos (JWT secret, API key, credenciales DB) a variables de entorno en lugar de dejarlos en `application.yml`.
- Se puede agregar un WebSocket o polling en el frontend para que el leaderboard se refresque solo, sin recargar.
