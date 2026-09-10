import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, timer, map, switchMap, shareReplay, of, catchError } from 'rxjs';

export interface SeasonInfo {
  uuid: string;
  displayName: string;
  title: string;
  startTime: Date;
  endTime: Date;
}

export interface CountdownData {
  actName: string;
  actTitle: string;
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  progressPct: number;
  isUrgent: boolean; // < 7 days
  isFinalDay: boolean; // < 24h
  formattedTime: string;
}

@Injectable({ providedIn: 'root' })
export class SeasonService {
  private seasonsCache$?: Observable<SeasonInfo[]>;

  constructor(private http: HttpClient) {}

  /**
   * Obtiene la lista de temporadas y actos oficiales de valorant-api.com
   */
  getSeasons(): Observable<SeasonInfo[]> {
    if (!this.seasonsCache$) {
      this.seasonsCache$ = this.http.get<{ data: any[] }>('https://valorant-api.com/v1/seasons').pipe(
        map(res => {
          return (res?.data || [])
            .filter(s => s.type === 'EAresSeasonType::Act' || (s.displayName && s.displayName.includes('ACT')))
            .map(s => ({
              uuid: s.uuid,
              displayName: s.displayName,
              title: s.title || s.displayName,
              startTime: new Date(s.startTime),
              endTime: new Date(s.endTime)
            }));
        }),
        catchError(() => {
          // Fallback en caso de error offline
          const now = new Date();
          const end = new Date(now.getTime() + 35 * 24 * 60 * 60 * 1000);
          return of([{
            uuid: 'fallback-act',
            displayName: 'ACTO V',
            title: 'V26 // ACTO V',
            startTime: new Date(now.getTime() - 20 * 24 * 60 * 60 * 1000),
            endTime: end
          }]);
        }),
        shareReplay(1)
      );
    }
    return this.seasonsCache$;
  }

  /**
   * Obtiene el acto actualmente activo según la fecha del sistema
   */
  getActiveAct(): Observable<SeasonInfo | null> {
    return this.getSeasons().pipe(
      map(seasons => {
        const now = new Date();
        // 1. Buscar acto donde startTime <= now <= endTime
        const current = seasons.find(s => s.startTime <= now && now <= s.endTime);
        if (current) return current;

        // 2. Si no, tomar el más cercano en el futuro o el último
        const upcoming = seasons.filter(s => s.endTime >= now).sort((a, b) => a.endTime.getTime() - b.endTime.getTime());
        if (upcoming.length > 0) return upcoming[0];

        return seasons.length > 0 ? seasons[seasons.length - 1] : null;
      })
    );
  }

  /**
   * Flujo reactivo que emite cada 1 segundo la cuenta regresiva en vivo
   */
  getLiveCountdown(): Observable<CountdownData | null> {
    return this.getActiveAct().pipe(
      switchMap(act => {
        if (!act) return of(null);
        return timer(0, 1000).pipe(
          map(() => this.calculateCountdown(act))
        );
      })
    );
  }

  private calculateCountdown(act: SeasonInfo): CountdownData {
    const now = new Date().getTime();
    const start = act.startTime.getTime();
    const end = act.endTime.getTime();

    const totalDuration = Math.max(1, end - start);
    const elapsed = Math.max(0, now - start);
    const progressPct = Math.min(100, Math.max(0, Math.round((elapsed / totalDuration) * 100)));

    const remainingMs = Math.max(0, end - now);
    const totalSeconds = Math.floor(remainingMs / 1000);

    const days = Math.floor(totalSeconds / (3600 * 24));
    const hours = Math.floor((totalSeconds % (3600 * 24)) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    const pad = (n: number) => n.toString().padStart(2, '0');
    const formattedTime = `${days}d ${pad(hours)}h ${pad(minutes)}m ${pad(seconds)}s`;

    return {
      actName: act.displayName,
      actTitle: act.title,
      days,
      hours,
      minutes,
      seconds,
      progressPct,
      isUrgent: days < 7,
      isFinalDay: days === 0 && hours < 24,
      formattedTime
    };
  }
}
