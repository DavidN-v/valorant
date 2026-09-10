export interface AgentStat {
  name: string;
  matches: number;
  winrate: number;
  kd: number;
}

export interface PlayerStats {
  user_id: number;
  display_name: string;
  riot_id: string;
  current_tier: string;
  ranking_in_tier: number;
  peak_tier: number;
  matches_played: number;
  wins: number;
  losses: number;
  winrate_pct: number;
  kd_ratio: number;
  headshot_pct: number;
  avg_combat_score: number;
  top_agents: AgentStat[];
  last_updated: string;
}
