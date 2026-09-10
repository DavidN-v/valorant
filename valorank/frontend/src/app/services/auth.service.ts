import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface RegisterPayload {
  display_name: string;
  email: string;
  password: string;
  riot_id: string; // "nombre#tag"
  region: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  user_id: number;
  display_name: string;
}

const API_URL = environment.apiUrl;
const TOKEN_KEY = 'valorank_token';

@Injectable({ providedIn: 'root' })
export class AuthService {

  constructor(private http: HttpClient) {}

  register(payload: RegisterPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_URL}/auth/register`, payload)
      .pipe(tap(res => this.storeToken(res.token)));
  }

  login(payload: LoginPayload): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_URL}/auth/login`, payload)
      .pipe(tap(res => this.storeToken(res.token)));
  }

  logout(): void {
    sessionStorage.removeItem(TOKEN_KEY);
  }

  getToken(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  private storeToken(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
  }
}
