import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PremierLeaderboardComponent } from './premier-leaderboard.component';

describe('PremierLeaderboardComponent', () => {
  let component: PremierLeaderboardComponent;
  let fixture: ComponentFixture<PremierLeaderboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PremierLeaderboardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PremierLeaderboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
