import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, shareReplay } from 'rxjs';

export interface MapCallout {
  regionName: string;
  superRegionName: string;
  fullName: string;
  xPct: number; // Porcentaje X en el minimapa (0 - 100)
  yPct: number; // Porcentaje Y en el minimapa (0 - 100)
}

export interface TacticalMap {
  uuid: string;
  displayName: string;
  tacticalDescription: string;
  coordinates: string;
  displayIcon: string;
  splash: string;
  callouts: MapCallout[];
}

@Injectable({ providedIn: 'root' })
export class MapService {
  private mapsCache$?: Observable<TacticalMap[]>;

  constructor(private http: HttpClient) {}

  getMaps(): Observable<TacticalMap[]> {
    if (!this.mapsCache$) {
      this.mapsCache$ = this.http.get<{ data: any[] }>('https://valorant-api.com/v1/maps').pipe(
        map(res => {
          const raw = res?.data || [];
          return raw
            .filter(m => m.displayIcon && m.callouts && m.callouts.length > 0 && !m.displayName.toLowerCase().includes('range') && !m.displayName.toLowerCase().includes('basic'))
            .map(m => {
              const xMult = m.xMultiplier || 0;
              const yMult = m.yMultiplier || 0;
              const xScalar = m.xScalarToAdd || 0;
              const yScalar = m.yScalarToAdd || 0;

              const callouts: MapCallout[] = (m.callouts || []).map((c: any) => {
                const loc = c.location || { x: 0, y: 0 };
                // Conversión de coordenadas 3D a porcentaje 2D del minimapa
                const xPct = (loc.y * xMult + xScalar) * 100;
                const yPct = (loc.x * yMult + yScalar) * 100;
                const superReg = c.superRegionName || '';
                const reg = c.regionName || '';
                const fullName = superReg && superReg !== reg ? `${superReg} ${reg}` : reg;

                return {
                  regionName: reg,
                  superRegionName: superReg,
                  fullName,
                  xPct: Math.max(2, Math.min(98, xPct)),
                  yPct: Math.max(2, Math.min(98, yPct))
                };
              });

              return {
                uuid: m.uuid,
                displayName: m.displayName,
                tacticalDescription: m.tacticalDescription || 'Zonas de Plantado',
                coordinates: m.coordinates || 'Coordenadas Clasificadas',
                displayIcon: m.displayIcon,
                splash: m.splash,
                callouts
              };
            });
        }),
        shareReplay(1)
      );
    }
    return this.mapsCache$;
  }
}
