import { api } from './api.js';
import { formatTime, resourceSuggestions, statusLabels } from './models.js';
import { el, field, grid, panel, heading, button, collection, empty, submitAction, notify } from './ui.js';

function trackRow(track = { id: crypto.randomUUID(), isActive: true }) {
  const name = field('Nombre de pista', 'trackName', { value: track.name });
  const surface = field('Superficie', 'surfaceType', { value: track.surfaceType, placeholder: 'Madera' });
  const active = field('Pista activa', 'isActive', { type: 'checkbox', required: false, checked: track.isActive });
  return { node: el('div', { className: 'row-card' }, grid(name, surface, active)), value: () => ({ id: track.id, name: name.input.value.trim(), surfaceType: surface.input.value.trim(), isActive: active.input.checked }) };
}
function judgeRow(judge = { id: crypto.randomUUID() }) {
  const name = field('Nombre del juez', 'judgeName', { value: judge.fullName });
  const specialty = field('Especialidad', 'specialty', { value: judge.specialty, placeholder: 'General' });
  return { node: el('div', { className: 'row-card' }, grid(name, specialty)), value: () => ({ id: judge.id, fullName: name.input.value.trim(), specialty: specialty.input.value.trim() }) };
}
export function mountScheduling(root, context) {
  const { challenge, category, rounds, registrations, edition } = context.data;
  if (!challenge) { root.append(empty('Esta edición todavía no tiene desafíos.')); return; }
  const teams = new Map(registrations.map(item => [item.teamId, item.team.name]));
  const schedule = panel(heading('Turnos programados', `${challenge.name} · ${category.name}`));
  if (!rounds.length) schedule.append(empty('Todavía no se programaron rondas para esta categoría.'));
  for (const round of rounds) {
    const tracks = new Map();
    for (const slot of round.slots) {
      if (!tracks.has(slot.track.id)) tracks.set(slot.track.id, { name: slot.track.name, slots: [] });
      tracks.get(slot.track.id).slots.push(slot);
    }
    schedule.append(el('h3', {}, `${round.name} · Ronda ${round.roundNumber}`, ' ', el('span', { className: 'badge' }, statusLabels[round.status] || round.status)),
      el('div', { className: 'schedule' }, [...tracks.values()].map(track => el('section', { className: 'track-column' }, el('h3', {}, track.name),
        track.slots.map(slot => el('button', { type: 'button', className: 'slot', onClick: () => context.openSlot(slot.slotId) },
          el('strong', {}, teams.get(slot.teamId) || slot.teamId), `${formatTime(slot.startTime)} → ${slot.endTime.slice(11, 16)}`,
          el('small', {}, `${slot.judges.map(judge => judge.fullName).join(', ')} · ${statusLabels[slot.status] || slot.status}`)))))));
  }
  root.append(schedule);
  const suggestions = resourceSuggestions(context.data.resourceRounds);
  const number = field('Número de ronda', 'roundNumber', { type: 'number', min: 1, step: 1 });
  const name = field('Nombre de la ronda', 'roundName', { placeholder: 'Clasificatoria' });
  const start = field('Inicio (hora local)', 'startTime', { type: 'datetime-local', value: `${edition.startDate}T10:00` });
  const duration = field('Duración de cada turno (segundos)', 'slotDurationSeconds', { type: 'number', min: 1, step: 1, value: 300 });
  const interval = field('Pausa de pista (segundos)', 'intervalSeconds', { type: 'number', min: 0, step: 1, value: 60 });
  const tracks = collection('Pistas', 'Agregar pista', trackRow, suggestions.tracks.length ? suggestions.tracks : [undefined]);
  const judges = collection('Jueces', 'Agregar juez', judgeRow, suggestions.judges.length ? suggestions.judges : [undefined]);
  const form = el('form', {}, el('fieldset', {}, grid(number, name, start, duration, interval), tracks.node, judges.node,
    el('p', { className: 'hint' }, 'Los recursos de rondas anteriores conservan su identidad. El servidor asigna un juez por turno y evita conflictos entre rondas; la pausa solo ocupa la pista.'),
    el('div', { className: 'actions' }, el('button', { type: 'submit' }, 'Programar ronda'))));
  form.addEventListener('submit', event => { event.preventDefault(); submitAction(form, context,
    () => api.schedule(challenge.id, { categoryId: category.id, roundNumber: Number(number.input.value), roundName: name.input.value.trim(),
      startTime: `${start.input.value}${start.input.value.length === 16 ? ':00' : ''}`, slotDurationSeconds: Number(duration.input.value),
      intervalSeconds: Number(interval.input.value), tracks: tracks.values(), judges: judges.values() }),
    async () => { await context.reload(); notify('La ronda se programó. Los horarios ya están disponibles.'); }); });
  root.append(panel(heading('Programar una ronda', 'El horario se calcula con los equipos inscriptos y los recursos disponibles.'), form));
}
