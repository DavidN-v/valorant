import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { LeaderboardComponent } from './leaderboard/leaderboard.component';
import { PremierLeaderboardComponent } from './premier-leaderboard/premier-leaderboard.component';
import { TacticalMapsComponent } from './tactical-maps/tactical-maps.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'leaderboard', component: LeaderboardComponent },
  { path: 'premier', component: PremierLeaderboardComponent },
  { path: 'maps', component: TacticalMapsComponent },
];

