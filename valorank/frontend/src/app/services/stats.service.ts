import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlayerStats } from '../models/player-stats.model';
import { environment } from '../../environments/environment';

const API_URL = environment.apiUrl;

@Injectable({ providedIn: 'root' })
export class StatsService {

  constructor(private http: HttpClient) {}

  getMyStats(): Observable<PlayerStats> {
    return this.http.get<PlayerStats>(`${API_URL}/me/stats`);
  }

  refreshMyStats(): Observable<PlayerStats> {
    return this.http.post<PlayerStats>(`${API_URL}/me/stats/refresh`, {});
  }

  getLeaderboard(sortBy: string = 'rr'): Observable<PlayerStats[]> {
    return this.http.get<PlayerStats[]>(`${API_URL}/leaderboard`, {
      params: { sortBy }
    });
  }

  /** Fuerza el refresh de stats de TODOS los usuarios (llama al scheduler manualmente) */
  refreshAll(): Observable<string> {
    return this.http.post(`${API_URL}/leaderboard/refresh-all`, {}, { responseType: 'text' });
  }

  getRefreshStatus(): Observable<{ inProgress: boolean, current: number, total: number }> {
    return this.http.get<{ inProgress: boolean, current: number, total: number }>(`${API_URL}/leaderboard/refresh-status`);
  }

  getPremierLeaderboard(sortBy: string = 'winrate'): Observable<PlayerStats[]> {
    return this.http.get<PlayerStats[]>(`${API_URL}/leaderboard/premier`, {
      params: { sortBy }
    });
  }

  refreshAllPremier(): Observable<string> {
    return this.http.post(`${API_URL}/leaderboard/premier/refresh-all`, {}, { responseType: 'text' });
  }

  getPremierRefreshStatus(): Observable<{ inProgress: boolean, current: number, total: number }> {
    return this.http.get<{ inProgress: boolean, current: number, total: number }>(`${API_URL}/leaderboard/premier/refresh-status`);
  }

  testDiscordWebhook(): Observable<{ success: boolean, message: string }> {
    return this.http.post<{ success: boolean, message: string }>(`${API_URL}/leaderboard/discord/test`, {});
  }
}
