export interface NextRankProgress {
  currentTierNumber: number;
  currentTierName: string;
  currentTierSpanish: string;
  currentIconUrl: string;
  currentRR: number;
  nextTierNumber: number | null;
  nextTierName: string | null;
  nextTierSpanish: string | null;
  nextIconUrl: string | null;
  rrNeeded: number;
  estimatedWins: number;
  progressPercentage: number;
  isMaxRank: boolean;
}

const TIER_NAMES = [
  'Unranked', 'Unused1', 'Unused2',
  'Iron 1', 'Iron 2', 'Iron 3',
  'Bronze 1', 'Bronze 2', 'Bronze 3',
  'Silver 1', 'Silver 2', 'Silver 3',
  'Gold 1', 'Gold 2', 'Gold 3',
  'Platinum 1', 'Platinum 2', 'Platinum 3',
  'Diamond 1', 'Diamond 2', 'Diamond 3',
  'Ascendant 1', 'Ascendant 2', 'Ascendant 3',
  'Immortal 1', 'Immortal 2', 'Immortal 3',
  'Radiant'
];

const TIER_NAMES_ES = [
  'Sin Rango', 'Unused1', 'Unused2',
  'Hierro 1', 'Hierro 2', 'Hierro 3',
  'Bronce 1', 'Bronce 2', 'Bronce 3',
  'Plata 1', 'Plata 2', 'Plata 3',
  'Oro 1', 'Oro 2', 'Oro 3',
  'Platino 1', 'Platino 2', 'Platino 3',
  'Diamante 1', 'Diamante 2', 'Diamante 3',
  'Ascendente 1', 'Ascendente 2', 'Ascendente 3',
  'Inmortal 1', 'Inmortal 2', 'Inmortal 3',
  'Radiante'
];

const EPISODE_TIER_UUID = '03621f52-342b-cf4e-4f86-9350a49c6d04';

export function getRankIconUrl(tierNumber: number): string {
  if (tierNumber < 0 || tierNumber >= TIER_NAMES.length) {
    return `https://media.valorant-api.com/competitivetiers/${EPISODE_TIER_UUID}/0/largeicon.png`;
  }
  return `https://media.valorant-api.com/competitivetiers/${EPISODE_TIER_UUID}/${tierNumber}/largeicon.png`;
}

export function parseTierNumber(tierNameOrNumber: string | number | undefined): number {
  if (tierNameOrNumber === undefined || tierNameOrNumber === null) return 0;
  if (typeof tierNameOrNumber === 'number') {
    if (tierNameOrNumber >= 0 && tierNameOrNumber < TIER_NAMES.length) return tierNameOrNumber;
    return 0;
  }
  const clean = tierNameOrNumber.trim().toLowerCase();
  for (let i = 0; i < TIER_NAMES.length; i++) {
    if (TIER_NAMES[i].toLowerCase() === clean || TIER_NAMES_ES[i].toLowerCase() === clean) {
      return i;
    }
  }
  return 0;
}

export function getSpanishTierName(tierNumber: number): string {
  if (tierNumber >= 0 && tierNumber < TIER_NAMES_ES.length) {
    return TIER_NAMES_ES[tierNumber];
  }
  return 'Sin Rango';
}

export function calculateRankProgress(currentTierName: string, rr: number): NextRankProgress {
  const currentTierNum = parseTierNumber(currentTierName);
  const safeRR = Math.max(0, Math.min(100, rr || 0));
  
  // Si es Radiant (rango maximo)
  if (currentTierNum >= 27) {
    return {
      currentTierNumber: 27,
      currentTierName: 'Radiant',
      currentTierSpanish: 'Radiante',
      currentIconUrl: getRankIconUrl(27),
      currentRR: safeRR,
      nextTierNumber: null,
      nextTierName: null,
      nextTierSpanish: null,
      nextIconUrl: null,
      rrNeeded: 0,
      estimatedWins: 0,
      progressPercentage: 100,
      isMaxRank: true
    };
  }

  // Si es Unranked
  if (currentTierNum < 3) {
    return {
      currentTierNumber: 0,
      currentTierName: 'Unranked',
      currentTierSpanish: 'Sin Rango',
      currentIconUrl: getRankIconUrl(0),
      currentRR: safeRR,
      nextTierNumber: 3,
      nextTierName: 'Iron 1',
      nextTierSpanish: 'Hierro 1',
      nextIconUrl: getRankIconUrl(3),
      rrNeeded: 100,
      estimatedWins: 5,
      progressPercentage: 0,
      isMaxRank: false
    };
  }

  const nextTierNum = currentTierNum + 1;
  const nextTierName = TIER_NAMES[nextTierNum];
  const nextTierSpanish = TIER_NAMES_ES[nextTierNum];
  const rrNeeded = Math.max(0, 100 - safeRR);
  // Estimamos ~20-22 RR por victoria
  const estimatedWins = Math.max(1, Math.ceil(rrNeeded / 20));

  return {
    currentTierNumber: currentTierNum,
    currentTierName: TIER_NAMES[currentTierNum],
    currentTierSpanish: TIER_NAMES_ES[currentTierNum],
    currentIconUrl: getRankIconUrl(currentTierNum),
    currentRR: safeRR,
    nextTierNumber: nextTierNum,
    nextTierName: nextTierName,
    nextTierSpanish: nextTierSpanish,
    nextIconUrl: getRankIconUrl(nextTierNum),
    rrNeeded: rrNeeded,
    estimatedWins: estimatedWins,
    progressPercentage: safeRR,
    isMaxRank: false
  };
}
