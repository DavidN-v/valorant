import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { routes } from './app/app.routes';
import { authInterceptor } from './app/services/auth.interceptor';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="global-tactical-bg" aria-hidden="true">
      <div class="global-grid-overlay"></div>
      <div class="global-ambient-glow global-glow-red"></div>
      <div class="global-ambient-glow global-glow-cyan"></div>
      <div class="global-ambient-glow global-glow-center"></div>
      <div class="global-tactical-ring global-ring-1"></div>
      <div class="global-tactical-ring global-ring-2"></div>
    </div>
    <div class="app-content-wrapper">
      <router-outlet></router-outlet>
    </div>
  `
})
export class AppComponent {}

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
  ]
});
