import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { StatsService } from '../services/stats.service';
import { PlayerStats } from '../models/player-stats.model';
import { getRankIconUrl, parseTierNumber, getSpanishTierName } from '../models/rank-data';
import { SeasonCountdownComponent } from '../season-countdown/season-countdown.component';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SeasonCountdownComponent],
  templateUrl: './leaderboard.component.html',
  styleUrl: './leaderboard.component.css'
})
export class LeaderboardComponent implements OnInit, OnDestroy {
  players: PlayerStats[] = [];
  searchTerm = '';
  sortBy = 'rr';
  refreshing = false;
  refreshMsg = '';
  
  refreshCurrent = 0;
  refreshTotal = 0;
  progressPct = 0;

  private pollInterval: any;
  private lastCurrent = -1;

  constructor(private statsService: StatsService) {}

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  load(): void {
    this.statsService.getLeaderboard(this.sortBy).subscribe(data => this.players = data);
  }

  get filteredPlayers(): PlayerStats[] {
    if (!this.searchTerm.trim()) return this.players;
    const term = this.searchTerm.toLowerCase().trim();
    return this.players.filter(p =>
      p.display_name?.toLowerCase().includes(term) ||
      p.riot_id?.toLowerCase().includes(term) ||
      p.current_tier?.toLowerCase().includes(term)
    );
  }

  getRankIcon(tierName: string): string {
    const tierNum = parseTierNumber(tierName);
    return getRankIconUrl(tierNum);
  }

  getRankSpanish(tierName: string): string {
    const tierNum = parseTierNumber(tierName);
    return getSpanishTierName(tierNum);
  }

  refreshAll(): void {
    if (this.refreshing) return;
    this.refreshing = true;
    this.refreshMsg = '';
    this.refreshCurrent = 0;
    this.refreshTotal = this.players.length || 1; // estimado inicial
    this.progressPct = 0;
    this.lastCurrent = -1;

    // El backend responde INMEDIATAMENTE y procesa en background
    this.statsService.refreshAll().subscribe({
      next: () => {
        this.startStatusPolling();
      },
      error: () => {
        this.refreshMsg = '❌ Error al iniciar la actualización.';
        this.refreshing = false;
      }
    });
  }

  private startStatusPolling(): void {
    this.stopPolling();
    // Poll inmediatamente primero y luego cada 1.5s
    this.checkStatus();
    this.pollInterval = setInterval(() => {
      this.checkStatus();
    }, 1500);
  }

  private checkStatus(): void {
    this.statsService.getRefreshStatus().subscribe({
      next: (status) => {
        if (!status.inProgress && status.total === 0) {
          // A veces el primer request llega antes de que el thread inicie
          return;
        }

        this.refreshCurrent = status.current;
        this.refreshTotal = status.total || 1;
        this.progressPct = (this.refreshCurrent / this.refreshTotal) * 100;

        // Si actualizó a un jugador nuevo, recargar la tabla para mostrar los cambios en vivo
        if (this.refreshCurrent > this.lastCurrent && this.lastCurrent !== -1) {
          this.load();
        }
        this.lastCurrent = this.refreshCurrent;

        // Si terminó
        if (!status.inProgress && status.total > 0 && status.current >= status.total) {
          this.stopPolling();
          this.refreshing = false;
          this.refreshMsg = '✅ Actualización completada';
          this.load(); // Carga final
          setTimeout(() => this.refreshMsg = '', 4000);
        }
      },
      error: () => {
        // Ignorar errores temporales de red
      }
    });
  }

  private stopPolling(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }
}
