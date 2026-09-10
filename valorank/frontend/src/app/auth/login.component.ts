import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

interface ShowcaseAgent {
  name: string;
  role: string;
  tagline: string;
  uuid: string;
  accentColor: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit, OnDestroy {
  mode: 'login' | 'register' = 'login';
  email = '';
  password = '';
  displayName = '';
  riotId = '';
  region = 'latam';
  error = '';
  successMessage = '';

  // Agentes en exhibición flotante
  readonly agents: ShowcaseAgent[] = [
    {
      name: 'Jett',
      role: 'DUELISTA',
      tagline: 'Viento ágil & precisión letal',
      uuid: 'add6443a-41bd-e414-f6ad-e58d267f4e95',
      accentColor: '#38bdf8'
    },
    {
      name: 'Omen',
      role: 'CONTROLADOR',
      tagline: 'Maestro de las sombras & visión',
      uuid: '8e253930-4c05-31dd-1b6c-968525494517',
      accentColor: '#c084fc'
    },
    {
      name: 'Reyna',
      role: 'DUELISTA',
      tagline: 'Dominio puro en el campo de batalla',
      uuid: 'a3bfb853-43b2-7238-a4f1-ad90e9e46bcc',
      accentColor: '#f43f5e'
    },
    {
      name: 'Waylay',
      role: 'DUELISTA',
      tagline: 'Fuerza implacable en el roster 2026',
      uuid: 'df1cb487-4902-002e-5c17-d28e83e78588',
      accentColor: '#f59e0b'
    }
  ];

  currentLeftAgent = 0;
  currentRightAgent = 1;
  private rotateInterval: any;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    // Rotar lentamente los agentes exhibidos cada 9 segundos
    this.rotateInterval = setInterval(() => {
      this.currentLeftAgent = (this.currentLeftAgent + 2) % this.agents.length;
      this.currentRightAgent = (this.currentRightAgent + 2) % this.agents.length;
    }, 9000);
  }

  ngOnDestroy(): void {
    if (this.rotateInterval) {
      clearInterval(this.rotateInterval);
    }
  }

  doLogin() {
    this.error = '';
    this.successMessage = '';
    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err) => this.error = err?.error?.message || 'Credenciales inválidas'
    });
  }

  doRegister() {
    this.error = '';
    this.successMessage = '';
    this.authService.register({
      display_name: this.displayName,
      email: this.email,
      password: this.password,
      riot_id: this.riotId,
      region: this.region
    }).subscribe({
      next: () => {
        this.successMessage = 'Usuario registrado exitosamente';
        setTimeout(() => this.router.navigate(['/dashboard']), 1500);
      },
      error: (err) => {
        this.error = err?.error?.message || 'No se pudo registrar';
      }
    });
  }
}
