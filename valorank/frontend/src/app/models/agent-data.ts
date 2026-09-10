export interface AgentDefinition {
  displayName: string;
  uuid: string;
  role: 'Duelista' | 'Iniciador' | 'Controlador' | 'Centinela';
  devName?: string;
}

export const ALL_VALORANT_AGENTS: AgentDefinition[] = [
  { displayName: 'Gekko',     uuid: 'e370fa57-4757-3604-3648-499e1f642d3f', role: 'Iniciador',   devName: 'Aggrobot' },
  { displayName: 'Fade',      uuid: 'dade69b4-4f5a-8528-247b-219e5a1facd6', role: 'Iniciador',   devName: 'BountyHunter' },
  { displayName: 'Breach',    uuid: '5f8d3a7f-467b-97f3-062c-13acf203c006', role: 'Iniciador',   devName: 'Breach' },
  { displayName: 'Deadlock',  uuid: 'cc8b64c8-4b25-4ff9-6e7f-37b4da43d235', role: 'Centinela',   devName: 'Cable' },
  { displayName: 'Tejo',      uuid: 'b444168c-4e35-8076-db47-ef9bf368f384', role: 'Iniciador',   devName: 'Cashew' },
  { displayName: 'Raze',      uuid: 'f94c3b30-42be-e959-889c-5aa313dba261', role: 'Duelista',    devName: 'Clay' },
  { displayName: 'Chamber',   uuid: '22697a3d-45bf-8dd7-4fec-84a9e28c69d7', role: 'Centinela',   devName: 'Deadeye' },
  { displayName: 'KAY/O',     uuid: '601dbbe7-43ce-be57-2a40-4abd24953621', role: 'Iniciador',   devName: 'Grenadier' },
  { displayName: 'Skye',      uuid: '6f2a04ca-43e0-be17-7f36-b3908627744d', role: 'Iniciador',   devName: 'Guide' },
  { displayName: 'Cypher',    uuid: '117ed9e3-49f3-6512-3ccf-0cada7e3823b', role: 'Centinela',   devName: 'Gumshoe' },
  { displayName: 'Sova',      uuid: '320b2a48-4d9b-a075-30f1-1f93a9b638fa', role: 'Iniciador',   devName: 'Hunter' },
  { displayName: 'Miks',      uuid: '7c8a4701-4de6-9355-b254-e09bc2a34b72', role: 'Controlador', devName: 'Iris' },
  { displayName: 'Killjoy',   uuid: '1e58de9c-4950-5125-93e9-a0aee9f98746', role: 'Centinela',   devName: 'Killjoy' },
  { displayName: 'Harbor',    uuid: '95b78ed7-4637-86d9-7e41-71ba8c293152', role: 'Controlador', devName: 'Mage' },
  { displayName: 'Vyse',      uuid: 'efba5359-4016-a1e5-7626-b1ae76895940', role: 'Centinela',   devName: 'Nox' },
  { displayName: 'Viper',     uuid: '707eab51-4836-f488-046a-cda6bf494859', role: 'Controlador', devName: 'Pandemic' },
  { displayName: 'Phoenix',   uuid: 'eb93336a-449b-9c1b-0a54-a891f7921d69', role: 'Duelista',    devName: 'Phoenix' },
  { displayName: 'Veto',      uuid: '92eeef5d-43b5-1d4a-8d03-b3927a09034b', role: 'Centinela',   devName: 'Pine' },
  { displayName: 'Astra',     uuid: '41fb69c1-4189-7b37-f117-bcaf1e96f1bf', role: 'Controlador', devName: 'Rift' },
  { displayName: 'Brimstone', uuid: '9f0d8ba9-4140-b941-57d3-a7ad57c6b417', role: 'Controlador', devName: 'Sarge' },
  { displayName: 'Iso',       uuid: '0e38b510-41a8-5780-5e8f-568b2a4f2d6c', role: 'Duelista',    devName: 'Sequoia' },
  { displayName: 'Clove',     uuid: '1dbf2edd-4729-0984-3115-daa5eed44993', role: 'Controlador', devName: 'Smonk' },
  { displayName: 'Neon',      uuid: 'bb2a4828-46eb-8cd1-e765-15848195d751', role: 'Duelista',    devName: 'Sprinter' },
  { displayName: 'Yoru',      uuid: '7f94d92c-4234-0a36-9646-3a87eb8b5c89', role: 'Duelista',    devName: 'Stealth' },
  { displayName: 'Waylay',    uuid: 'df1cb487-4902-002e-5c17-d28e83e78588', role: 'Duelista',    devName: 'Terra' },
  { displayName: 'Sage',      uuid: '569fdd95-4d10-43ab-ca70-79becc718b46', role: 'Centinela',   devName: 'Thorne' },
  { displayName: 'Reyna',     uuid: 'a3bfb853-43b2-7238-a4f1-ad90e9e46bcc', role: 'Duelista',    devName: 'Vampire' },
  { displayName: 'Omen',      uuid: '8e253930-4c05-31dd-1b6c-968525494517', role: 'Controlador', devName: 'Wraith' },
  { displayName: 'Jett',      uuid: 'add6443a-41bd-e414-f6ad-e58d267f4e95', role: 'Duelista',    devName: 'Wushu' }
];

// Lookup maps indexados por nombre y lowercase para evitar cualquier fallo de tipeo o mayusculas
const UUID_BY_NAME: Record<string, string> = {};
const ROLE_BY_NAME: Record<string, string> = {};
const DISPLAY_NAME_BY_KEY: Record<string, string> = {};

ALL_VALORANT_AGENTS.forEach(agent => {
  const nameLower = agent.displayName.toLowerCase();
  UUID_BY_NAME[agent.displayName] = agent.uuid;
  UUID_BY_NAME[nameLower] = agent.uuid;

  ROLE_BY_NAME[agent.displayName] = agent.role;
  ROLE_BY_NAME[nameLower] = agent.role;

  DISPLAY_NAME_BY_KEY[agent.displayName] = agent.displayName;
  DISPLAY_NAME_BY_KEY[nameLower] = agent.displayName;

  // Registrar tambien por devName para compatibilidad si la API devuelve el nombre interno de Riot
  if (agent.devName) {
    const devLower = agent.devName.toLowerCase();
    UUID_BY_NAME[agent.devName] = agent.uuid;
    UUID_BY_NAME[devLower] = agent.uuid;

    ROLE_BY_NAME[agent.devName] = agent.role;
    ROLE_BY_NAME[devLower] = agent.role;

    DISPLAY_NAME_BY_KEY[agent.devName] = agent.displayName;
    DISPLAY_NAME_BY_KEY[devLower] = agent.displayName;
  }
});

// UUID regex para detectar si el string ya viene como UUID
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function resolveAgentUuid(nameOrUuid: string): string {
  if (!nameOrUuid) return '';
  const trimmed = nameOrUuid.trim();
  if (UUID_REGEX.test(trimmed)) {
    return trimmed.toLowerCase();
  }
  return UUID_BY_NAME[trimmed] || UUID_BY_NAME[trimmed.toLowerCase()] || '';
}

export function resolveAgentRole(nameOrUuid: string): string {
  if (!nameOrUuid) return 'Agente';
  const trimmed = nameOrUuid.trim();
  return ROLE_BY_NAME[trimmed] || ROLE_BY_NAME[trimmed.toLowerCase()] || 'Agente';
}

export function resolveAgentDisplayName(nameOrUuid: string): string {
  if (!nameOrUuid) return 'Desconocido';
  const trimmed = nameOrUuid.trim();
  return DISPLAY_NAME_BY_KEY[trimmed] || DISPLAY_NAME_BY_KEY[trimmed.toLowerCase()] || trimmed;
}
