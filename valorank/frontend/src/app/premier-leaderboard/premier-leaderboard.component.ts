import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { StatsService } from '../services/stats.service';
import { PlayerStats } from '../models/player-stats.model';
import { resolveAgentUuid, resolveAgentRole, resolveAgentDisplayName } from '../models/agent-data';
import { SeasonCountdownComponent } from '../season-countdown/season-countdown.component';

@Component({
  selector: 'app-premier-leaderboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SeasonCountdownComponent],
  templateUrl: './premier-leaderboard.component.html',
  styleUrl: './premier-leaderboard.component.css'
})
export class PremierLeaderboardComponent implements OnInit, OnDestroy {
  players: PlayerStats[] = [];
  searchTerm = '';
  sortBy = 'winrate';
  refreshing = false;
  refreshMsg = '';
  
  refreshCurrent = 0;
  refreshTotal = 0;
  progressPct = 0;

  expandedPlayerId: number | null = null;

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
    this.statsService.getPremierLeaderboard(this.sortBy)
      .subscribe(data => this.players = data);
  }

  get filteredPlayers(): PlayerStats[] {
    if (!this.searchTerm.trim()) return this.players;
    const term = this.searchTerm.toLowerCase().trim();
    return this.players.filter(p =>
      p.display_name?.toLowerCase().includes(term) ||
      p.riot_id?.toLowerCase().includes(term)
    );
  }

  toggleExpand(userId: number): void {
    if (this.expandedPlayerId === userId) {
      this.expandedPlayerId = null;
    } else {
      this.expandedPlayerId = userId;
    }
  }

  getAgentUuid(name: string): string {
    return resolveAgentUuid(name);
  }

  getAgentRole(name: string): string {
    return resolveAgentRole(name);
  }

  getAgentDisplayName(name: string): string {
    return resolveAgentDisplayName(name);
  }

  getRoleClass(name: string): string {
    const role = this.getAgentRole(name);
    return 'role-' + role.toLowerCase();
  }

  onImgError(event: Event, agentName: string): void {
    const img = event.target as HTMLImageElement;
    img.style.display = 'none';
    const parent = img.parentElement;
    if (parent) {
      const div = document.createElement('div');
      div.style.cssText = 'width:28px;height:28px;display:flex;align-items:center;justify-content:center;font-family:Oswald,sans-serif;font-size:14px;font-weight:700;color:#fbbf24;background:rgba(245,158,11,0.2);border-radius:50%;';
      div.textContent = agentName.charAt(0).toUpperCase();
      parent.insertBefore(div, img);
    }
  }

  refreshAll(): void {
    if (this.refreshing) return;
    this.refreshing = true;
    this.refreshMsg = '';
    this.refreshCurrent = 0;
    this.refreshTotal = this.players.length || 1;
    this.progressPct = 0;
    this.lastCurrent = -1;

    this.statsService.refreshAllPremier().subscribe({
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
    this.checkStatus();
    this.pollInterval = setInterval(() => {
      this.checkStatus();
    }, 1500);
  }

  private checkStatus(): void {
    this.statsService.getPremierRefreshStatus().subscribe({
      next: (status) => {
        if (!status.inProgress && status.total === 0) {
          return;
        }

        this.refreshCurrent = status.current;
        this.refreshTotal = status.total || 1;
        this.progressPct = (this.refreshCurrent / this.refreshTotal) * 100;

        if (this.refreshCurrent > this.lastCurrent && this.lastCurrent !== -1) {
          this.load();
        }
        this.lastCurrent = this.refreshCurrent;

        if (!status.inProgress && status.total > 0 && status.current >= status.total) {
          this.stopPolling();
          this.refreshing = false;
          this.refreshMsg = '✅ Actualización completada';
          this.load();
          setTimeout(() => this.refreshMsg = '', 4000);
        }
      },
      error: () => {}
    });
  }

  private stopPolling(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }
}
