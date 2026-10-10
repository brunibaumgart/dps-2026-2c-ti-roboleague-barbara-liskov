import { api } from './api.js';
import { appealLabels, attemptId, formatTime, slotsFor, sourceLabels } from './models.js';
import { el, field, choice, grid, panel, heading, table, empty, button, submitAction, notify } from './ui.js';

export function mountAppeals(root, context) {
  const { challenge, category, registrations, rounds } = context.data;
  if (!challenge) { root.append(empty('Elegí un desafío para consultar sus apelaciones.')); return; }
  const slots = slotsFor(rounds);
  const teams = new Map(registrations.map(item => [item.teamId, item.team.name]));
  const content = el('div');
  let requestVersion = 0;
  const refresh = button('Consultar apelaciones', () => load());
  root.append(panel(heading('Apelaciones de la categoría', 'El servidor conserva cada transicion. Presentar, revisar o resolver no inventa estados en el navegador.'),
    el('div', { className: 'actions' }, refresh)), content);

  async function load() {
    const version = ++requestVersion;
    content.replaceChildren(empty('Consultando las apelaciones…'));
    const current = () => context.isCurrent() && requestVersion === version;
    try {
      const appeals = await api.appeals(challenge.id, category.id);
      if (!current()) return;
      content.replaceChildren();
      if (slots.length) content.append(fileForm(slots, teams, context, () => load()));
      else content.append(empty('No hay turnos en esta categoría para presentar un reclamo.'));
      content.append(panel(heading('Reclamos presentados', `${appeals.length} en ${challenge.name} · ${category.name}`),
        appeals.length ? table(['Equipo', 'Intento', 'Estado', 'Motivo', 'Presentada', 'Revisor'], appeals.map(appeal => [
          teams.get(appeal.teamId) || appeal.teamId, appeal.attemptId, appealLabels[appeal.status] || appeal.status,
          appeal.reason, formatTime(appeal.submittedAt), appeal.reviewerId || '—',
        ])) : empty('Todavía no hay apelaciones en esta categoría.')));
      appeals.forEach(appeal => content.append(appealCard(appeal, teams, context, () => load())));
    } catch (error) {
      if (current()) { content.replaceChildren(empty('No se pudieron consultar las apelaciones. Volvé a consultar o recargá los datos.')); notify(error.message, error.details, true); }
    }
  }
  load();
}

function fileForm(slots, teams, context, reload) {
  const slot = choice('Turno', 'appealSlot', slots.map(item => ({ value: item.slotId,
    label: `${teams.get(item.teamId) || item.teamId} · ${item.roundName} · ${formatTime(item.startTime)}` })), slots[0].slotId);
  const number = field('Número de intento', 'appealAttempt', { type: 'number', min: 1, step: 1, value: 1 });
  const reason = field('Motivo', 'reason');
  const evidence = field('Evidencia', 'evidence');
  const form = el('form', {}, el('fieldset', {}, grid(slot, number, reason, evidence),
    el('div', { className: 'actions' }, el('button', { type: 'submit' }, 'Presentar apelación'))));
  form.addEventListener('submit', event => {
    event.preventDefault();
    const selected = slots.find(item => item.slotId === slot.input.value);
    submitAction(form, context, () => api.fileAppeal(attemptId(selected.slotId, Number(number.input.value)), {
      teamId: selected.teamId, reason: reason.input.value.trim(), evidence: evidence.input.value.trim(),
    }), async () => { await reload(); notify('La apelación se presentó. El estado lo confirmó el servidor.'); });
  });
  return panel(heading('Presentar un reclamo', 'El equipo del turno es el que reclama. El intento tiene que existir en el servidor.'), form);
}

function appealCard(appeal, teams, context, reload) {
  const title = `${teams.get(appeal.teamId) || appeal.teamId} · ${appeal.attemptId}`;
  const body = [el('p', {}, `Estado: ${appealLabels[appeal.status] || appeal.status}`),
    el('p', {}, appeal.reason), appeal.evidence ? el('p', {}, `Evidencia: ${appeal.evidence}`) : null,
    appeal.resolutionNotes ? el('p', {}, `Resolución: ${appeal.resolutionNotes}`) : null];
  if (appeal.status === 'PENDING') body.push(reviewForm(appeal, context, reload));
  if (appeal.status === 'UNDER_REVIEW') body.push(resolveForm(appeal, context, reload));
  return panel(heading(title, `Presentada ${formatTime(appeal.submittedAt)}`), ...body);
}

function reviewForm(appeal, context, reload) {
  const reviewer = field('Revisor', 'reviewerId');
  const form = el('form', {}, el('fieldset', {}, grid(reviewer),
    el('div', { className: 'actions' }, el('button', { type: 'submit' }, 'Tomar en revisión'))));
  form.addEventListener('submit', event => {
    event.preventDefault();
    submitAction(form, context, () => api.reviewAppeal(appeal.id, { reviewerId: reviewer.input.value.trim() }),
      async () => { await reload(); notify('La apelación quedó en revisión.'); });
  });
  return form;
}

function resolveForm(appeal, context, reload) {
  const reviewer = field('Revisor', 'reviewerId', { value: appeal.reviewerId || '' });
  const notes = field('Notas de resolución', 'notes');
  const time = field('Tiempo (segundos)', 'timeSeconds', { type: 'number', min: 0, step: 'any', required: false });
  const objectives = field('Objetivos', 'objectives', { type: 'number', min: 0, step: 1, required: false });
  const penalties = field('Faltas', 'penalties', { type: 'number', min: 0, step: 1, required: false });
  const consumption = field('Consumo', 'consumption', { type: 'number', min: 0, step: 'any', required: false });
  const actions = el('div', { className: 'actions' },
    el('button', { type: 'submit', name: 'accept' }, 'Aceptar con corrección'),
    el('button', { type: 'button', className: 'secondary', onClick: event => reject(event.target.form) }, 'Rechazar'));
  const form = el('form', {}, el('fieldset', {}, grid(reviewer, notes),
    el('p', { className: 'hint' }, `Si se acepta, las mediciones reemplazan ${sourceLabels.AUTOMATIC_MEASUREMENTS}. El reglamento del intento lo decide el servidor.`),
    grid(time, objectives, penalties, consumption), actions));
  function reject(target) {
    submitAction(target, context, () => api.rejectAppeal(appeal.id, {
      reviewerId: reviewer.input.value.trim(), notes: notes.input.value.trim(),
    }), async () => { await reload(); notify('La apelación fue rechazada. El puntaje original se conserva.'); });
  }
  form.addEventListener('submit', event => {
    event.preventDefault();
    const measurements = {};
    if (time.input.value !== '') measurements.timeSeconds = Number(time.input.value);
    if (objectives.input.value !== '') measurements.objectives = Number(objectives.input.value);
    if (penalties.input.value !== '') measurements.penalties = Number(penalties.input.value);
    if (consumption.input.value !== '') measurements.consumption = Number(consumption.input.value);
    submitAction(form, context, () => api.acceptAppeal(appeal.id, {
      reviewerId: reviewer.input.value.trim(), notes: notes.input.value.trim(),
      ...(Object.keys(measurements).length ? { measurements } : {}),
    }), async result => { await reload(); notify(`La apelación se aceptó. Quedó la tabla v${result.standingsVersion}.`); });
  });
  return form;
}
