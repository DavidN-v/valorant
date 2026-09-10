import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SeasonService, CountdownData } from '../services/season.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-season-countdown',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './season-countdown.component.html',
  styleUrl: './season-countdown.component.css'
})
export class SeasonCountdownComponent implements OnInit, OnDestroy {
  countdown: CountdownData | null = null;
  private sub?: Subscription;

  constructor(private seasonService: SeasonService) {}

  ngOnInit(): void {
    this.sub = this.seasonService.getLiveCountdown().subscribe(data => {
      this.countdown = data;
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  pad(n: number): string {
    return n.toString().padStart(2, '0');
  }
}
