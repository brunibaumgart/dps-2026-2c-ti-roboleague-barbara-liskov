export const sourceLabels = { AUTOMATIC_MEASUREMENTS: 'Mediciones de pista', JUDGE_PANEL: 'Panel de jueces' };
export const statusLabels = { SCHEDULED: 'Programado', IN_PROGRESS: 'En curso', COMPLETED: 'Completado', CANCELLED: 'Cancelado',
  AWAITING_SOURCES: 'Esperando fuentes', EVALUATED: 'Evaluado', ADJUSTED: 'Ajustado', UNDER_APPEAL: 'En apelación', DISQUALIFIED: 'Descalificado' };
export const appealLabels = { PENDING: 'Pendiente', UNDER_REVIEW: 'En revisión', ACCEPTED: 'Aceptada', REJECTED: 'Rechazada' };
export const standingsLabels = { PROVISIONAL: 'Provisional', OFFICIAL: 'Oficial', REPLACED: 'Reemplazada' };
export function attemptId(slotId, number) {
  if (!slotId || !Number.isSafeInteger(number) || number < 1) throw new Error('El número de intento debe ser un entero positivo.');
  return `${slotId}-${number}`;
}
export function teamCandidate(team) {
  return { id: team.id, name: team.name, institution: team.institution,
    members: team.members.map(({ id, fullName, birthDate, role }) => ({ id, fullName, birthDate, role })),
    robot: { ...team.robot, sensors: [...team.robot.sensors] },
    documentation: { documents: { ...team.documentation.documents }, verifiedBy: team.documentation.verified ? team.documentation.verifiedBy : null } };
}
export function metricsFor(rulebook, source) { return rulebook.metrics.filter(metric => metric.source === source); }
export function resourceSuggestions(rounds) {
  const tracks = new Map(), judges = new Map();
  for (const round of rounds) for (const slot of round.slots) {
    tracks.set(slot.track.id, { ...slot.track });
    for (const judge of slot.judges) judges.set(judge.id, { ...judge });
  }
  return { tracks: [...tracks.values()], judges: [...judges.values()] };
}
export function slotsFor(rounds) {
  return rounds.flatMap(round => round.slots.map(slot => ({ ...slot, roundName: round.name, roundId: round.roundId })))
    .sort((a, b) => a.startTime.localeCompare(b.startTime) || a.track.id.localeCompare(b.track.id) || a.slotId.localeCompare(b.slotId));
}
export function formatTime(value) { return value ? value.replace('T', ' · ').slice(0, 18) : '—'; }
