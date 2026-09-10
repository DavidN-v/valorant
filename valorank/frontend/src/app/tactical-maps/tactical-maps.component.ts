import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MapService, TacticalMap, MapCallout } from '../services/map.service';
import { SeasonCountdownComponent } from '../season-countdown/season-countdown.component';

@Component({
  selector: 'app-tactical-maps',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SeasonCountdownComponent],
  templateUrl: './tactical-maps.component.html',
  styleUrl: './tactical-maps.component.css'
})
export class TacticalMapsComponent implements OnInit {
  maps: TacticalMap[] = [];
  selectedMap: TacticalMap | null = null;
  selectedCallout: MapCallout | null = null;
  hoveredCallout: MapCallout | null = null;

  searchQuery = '';
  selectedFilterSector = 'ALL';
  showAllLabels = false;
  loading = true;

  constructor(private mapService: MapService) {}

  ngOnInit(): void {
    this.mapService.getMaps().subscribe({
      next: (data) => {
        this.maps = data;
        if (this.maps.length > 0) {
          // Seleccionar Ascent por defecto o el primero
          const ascent = this.maps.find(m => m.displayName.toLowerCase() === 'ascent');
          this.selectMap(ascent || this.maps[0]);
        }
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  selectMap(map: TacticalMap): void {
    this.selectedMap = map;
    this.selectedCallout = null;
    this.hoveredCallout = null;
    this.searchQuery = '';
    this.selectedFilterSector = 'ALL';
  }

  get availableSectors(): string[] {
    if (!this.selectedMap) return [];
    const sectors = new Set<string>();
    for (const c of this.selectedMap.callouts) {
      if (c.superRegionName && c.superRegionName.trim()) {
        sectors.add(c.superRegionName.trim());
      }
    }
    return Array.from(sectors).sort();
  }

  get filteredCallouts(): MapCallout[] {
    if (!this.selectedMap) return [];
    return this.selectedMap.callouts.filter(c => {
      const matchesSearch = !this.searchQuery ||
        c.fullName.toLowerCase().includes(this.searchQuery.toLowerCase()) ||
        c.regionName.toLowerCase().includes(this.searchQuery.toLowerCase());

      const matchesSector = this.selectedFilterSector === 'ALL' ||
        c.superRegionName === this.selectedFilterSector;

      return matchesSearch && matchesSector;
    });
  }

  onCalloutClick(callout: MapCallout): void {
    this.selectedCallout = callout;
    this.hoveredCallout = callout;
  }

  onCalloutHover(callout: MapCallout | null): void {
    this.hoveredCallout = callout;
  }
}
