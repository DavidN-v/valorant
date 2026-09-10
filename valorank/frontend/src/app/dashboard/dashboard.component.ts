import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { StatsService } from '../services/stats.service';
import { PlayerStats } from '../models/player-stats.model';
import { resolveAgentUuid, resolveAgentDisplayName } from '../models/agent-data';
import { calculateRankProgress, NextRankProgress } from '../models/rank-data';
import { SeasonCountdownComponent } from '../season-countdown/season-countdown.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, SeasonCountdownComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  stats?: PlayerStats;
  loaded = false;
  refreshing = false;
  refreshError = '';
  testingDiscord = false;
  discordStatusMessage = '';

  constructor(private statsService: StatsService) {}

  ngOnInit(): void {
    this.statsService.getMyStats().subscribe({
      next: (s) => { this.stats = s; this.loaded = true; },
      error: () => { this.loaded = true; }
    });
  }

  get rankProgress(): NextRankProgress | null {
    if (!this.stats) return null;
    return calculateRankProgress(this.stats.current_tier, this.stats.ranking_in_tier);
  }

  refresh(): void {
    this.refreshing = true;
    this.refreshError = '';
    this.statsService.refreshMyStats().subscribe({
      next: (s) => { this.stats = s; this.refreshing = false; },
      error: (err) => {
        this.refreshing = false;
        this.refreshError = err?.error?.message || 'Error al actualizar. ¿Tu Riot ID es correcto?';
      }
    });
  }

  testDiscord(): void {
    this.testingDiscord = true;
    this.discordStatusMessage = '';
    this.statsService.testDiscordWebhook().subscribe({
      next: (res) => {
        this.testingDiscord = false;
        this.discordStatusMessage = res.message || '¡Notificación enviada a Discord!';
        setTimeout(() => this.discordStatusMessage = '', 5000);
      },
      error: () => {
        this.testingDiscord = false;
        this.discordStatusMessage = 'Error al conectar con Discord.';
        setTimeout(() => this.discordStatusMessage = '', 5000);
      }
    });
  }

  onImgError(event: Event, agentName: string): void {
    const img = event.target as HTMLImageElement;
    img.style.display = 'none';
    const parent = img.parentElement;
    if (parent) {
      const div = document.createElement('div');
      div.style.cssText = 'width:64px;height:64px;display:flex;align-items:center;justify-content:center;font-family:Oswald,sans-serif;font-size:28px;font-weight:700;color:rgba(255,70,85,0.7)';
      div.textContent = agentName.charAt(0).toUpperCase();
      parent.insertBefore(div, img);
    }
  }

  getAgentUuid(name: string): string {
    return resolveAgentUuid(name);
  }

  getAgentDisplayName(name: string): string {
    return resolveAgentDisplayName(name);
  }
}
