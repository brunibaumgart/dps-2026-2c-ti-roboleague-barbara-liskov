import { api } from './api.js';
import { formatTime, metricsFor, resourceSuggestions, slotsFor, sourceLabels, statusLabels } from './models.js';
import { el, field, choice, grid, panel, heading, table, empty, button, collection, submitAction, notify } from './ui.js';

const units = { COUNT: 'cantidad', SECONDS: 'segundos', METERS: 'metros', RATIO: 'proporción', POINTS: 'puntos' };
function metricFields(rulebook, source) {
  return metricsFor(rulebook, source).map(metric => {
    const label = { salidas_de_linea: 'Salidas de línea', vuelta_rapida: 'Vuelta rápida' }[metric.name] || metric.name.replaceAll('_', ' ');
    const item = field(label, metric.name, { type: 'number', min: metric.range.min,
      max: metric.range.max ?? undefined, step: metric.unit === 'COUNT' ? 1 : 'any' });
    item.node.append(el('span', { className: 'metric-help' }, `${units[metric.unit] || metric.unit} · mínimo ${metric.range.min}${metric.range.max == null ? '' : ` · máximo ${metric.range.max}`}`));
    return item;
  });
}
function scoreRow(judges, initial = judges[0]) {
  const judge = choice('Juez del panel', 'panelJudge', judges.map(item => ({ value: item.id, label: item.fullName })), initial.id);
  const score = field('Nota del juez', 'judgeScore', { type: 'number', min: 0, step: 'any' });
  return { node: el('div', { className: 'row-card' }, grid(judge, score)), value: () => [judge.input.value, Number(score.input.value)] };
}
export function renderBreakdown(data, rulebook) {
  const received = rulebook.requiredSources.filter(source => !data.awaiting.includes(source));
  const summary = panel(heading('Desglose del intento', `Reglamento v${data.rulebookVersion} · ${statusLabels[data.status] || data.status}`),
    el('div', { className: 'score' }, data.score == null ? (data.awaiting.length ? 'Pendiente' : 'Sin puntaje computable') : `${data.score} puntos`),
    el('p', {}, received.length ? `Recibidas: ${received.map(source => sourceLabels[source] || source).join(' · ')}` : 'Todavía no se recibió una fuente.'),
    data.awaiting.length ? el('p', {}, `Pendientes: ${data.awaiting.map(source => sourceLabels[source] || source).join(' · ')}`) : null);
  summary.append(data.items.length ? table(['Concepto', 'Medición', 'Fórmula aplicada', 'Subtotal'], data.items.map(item => [item.concept, item.rawMetric, item.formula, item.subtotal]))
    : empty('El desglose aparece cuando están completas las fuentes requeridas.'));
  if (data.notes.length) summary.append(el('ul', {}, data.notes.map(note => el('li', {}, note))));
  const contributions = panel(heading('Aportes por fuente', 'Subtotales calculados por el servidor; el total incluye topes y ajustes del reglamento.'));
  contributions.append(data.bySource.length ? table(['Fuente', 'Subtotal'], data.bySource.map(source => [source.label, source.subtotal])) : empty('No hay aportes disponibles.'));
  const history = panel(heading('Historial de revisiones', 'Se conserva el resultado de cada revisión, su autor, motivo y versión.'));
  history.append(data.revisions.length ? table(['Revisión', 'Reglamento', 'Puntaje', 'Autor', 'Motivo', 'Fecha'], data.revisions.map(revision => [
    revision.number, `v${revision.rulebookVersion}`, revision.total, revision.authorId, revision.reason, formatTime(revision.timestamp),
  ])) : empty('Este intento todavía no tiene revisiones puntuadas.'));
  return [summary, contributions, history];
}
function attemptsPanel(attempts, shown, show) {
  return panel(heading('Intentos del turno', 'El servidor los numera en el orden en que llegaron los resultados.'),
    table(['Intento', 'Estado', 'Fuentes recibidas', 'Puntaje', ''], attempts.map(attempt => [
      attempt.number, statusLabels[attempt.status] || attempt.status,
      attempt.received.map(source => sourceLabels[source] || source).join(' · ') || '—',
      attempt.score == null ? '—' : attempt.score,
      attempt.id === shown.id ? 'Visto' : button('Ver desglose', () => show(attempt.id), 'link-button'),
    ])));
}
export function mountResults(root, context, readOnly = false) {
  const { challenge, registrations, rounds } = context.data;
  if (!challenge) { root.append(empty('Elegí un desafío para consultar sus intentos.')); return; }
  const slots = slotsFor(rounds);
  if (!slots.length) { root.append(empty('No hay turnos programados para este desafío y categoría. Primero programá una ronda.')); return; }
  const teams = new Map(registrations.map(item => [item.teamId, item.team.name]));
  const slotChoice = choice('Turno', 'selectedSlot', slots.map(slot => ({ value: slot.slotId,
    label: `${teams.get(slot.teamId) || slot.teamId} · ${slot.roundName} · ${slot.track.name} · ${formatTime(slot.startTime)}` })), context.selectedSlot || slots[0].slotId);
  if (!slotChoice.input.value) slotChoice.input.value = slots[0].slotId;
  const content = el('div');
  let requestVersion = 0;
  let busy = false;
  const refresh = button('Consultar turno', () => load());
  root.append(panel(heading(readOnly ? 'Consultar desglose' : 'Cargar un resultado', 'Elegí el turno. Los intentos se numeran solos en el orden en que llegan los resultados y cada uno conserva el reglamento con el que comenzó.'), grid(slotChoice), el('div', { className: 'actions' }, refresh)), content);
  const rulebookOf = version => version === challenge.currentRulebook.version ? challenge.currentRulebook : api.rulebook(challenge.id, version);
  async function load(selectedAttempt) {
    if (busy) return;
    const version = ++requestVersion;
    const slot = slots.find(item => item.slotId === slotChoice.input.value);
    content.replaceChildren(empty('Consultando el turno…'));
    const current = () => context.isCurrent() && requestVersion === version;
    try {
      const turn = await api.turnResults(slot.slotId);
      if (!current()) return;
      const { attempts, attemptRule } = turn;
      const latest = attempts.at(-1);
      const shown = attempts.find(item => item.id === selectedAttempt) || latest;
      const pending = latest?.status === 'AWAITING_SOURCES';
      // An attempt awaiting a source keeps its own version; a new attempt opens with the one the category plays with.
      const [breakdown, shownRulebook, rulebook, pendingBreakdown] = await Promise.all([shown ? api.breakdown(shown.id) : null,
        shown ? rulebookOf(shown.rulebookVersion) : null, rulebookOf(pending ? latest.rulebookVersion : turn.rulebookVersion),
        pending && shown !== latest ? api.breakdown(latest.id) : null]);
      if (!current()) return;
      content.replaceChildren();
      const results = shown ? [attemptsPanel(attempts, shown, id => load(id)), ...renderBreakdown(breakdown, shownRulebook)] : [];
      if (readOnly) {
        content.append(...(shown ? results : [panel(heading('Sin resultados todavía'), empty('Este turno todavía no tiene intentos. La primera captura abre el intento 1.'), button('Cargar resultado', () => context.openSlot(slot.slotId)))]));
        return;
      }
      if (slot.status === 'CANCELLED') { content.append(empty('El turno está cancelado. Elegí otro turno.'), ...results); return; }
      if (!turn.acceptsResult) {
        content.append(panel(heading('Turno completo', teams.get(slot.teamId) || slot.teamId), empty(`El turno ya corrió ${attempts.length} intento(s) y el reglamento v${turn.rulebookVersion} admite ${attemptRule}. No se reciben más resultados.`)), ...results);
        return;
      }
      const available = pending ? (pendingBreakdown || breakdown).awaiting : rulebook.requiredSources;
      if (!available.length) { content.append(empty('Este reglamento no declara fuentes para capturar.'), ...results); return; }
      const judge = choice('Juez que carga la fuente', 'assignedJudge', slot.judges.map(item => ({ value: item.id, label: item.fullName })));
      const source = choice('Fuente de medición', 'source', available.map(item => ({ value: item, label: sourceLabels[item] || item })));
      const fields = el('div');
      const target = pending
        ? `Completa el intento ${latest.number}, que espera esta fuente, con su reglamento v${rulebook.version}.`
        : `Abre el intento ${attempts.length + 1} con el reglamento v${rulebook.version}, el que usa la categoría. El número lo asigna el servidor en el orden de llegada.`;
      const form = el('form', {}, el('fieldset', {}, grid(judge, source), el('p', { className: 'hint' }, `${target} El reglamento admite ${attemptRule}. Elegí una fuente pendiente; no se reemplazan capturas previas.`), fields,
        el('div', { className: 'actions' }, el('button', { type: 'submit' }, 'Guardar resultado'))));
      let fixed = [], metrics = [], scores;
      function renderSource() {
        metrics = metricFields(rulebook, source.input.value);
        fields.replaceChildren();
        if (source.input.value === 'AUTOMATIC_MEASUREMENTS') {
          fixed = [field('Tiempo de recorrido (segundos)', 'timeSeconds', { type: 'number', min: 0, step: 'any' }),
            field('Objetivos alcanzados', 'objectives', { type: 'number', min: 0, step: 1 }),
            field('Faltas', 'penalties', { type: 'number', min: 0, step: 1 }),
            field('Consumo', 'consumption', { type: 'number', min: 0, step: 'any' })];
          fields.append(grid(...fixed));
        } else {
          const known = new Map(resourceSuggestions(context.data.resourceRounds).judges.map(item => [item.id, item]));
          slot.judges.forEach(item => known.set(item.id, item));
          scores = collection('Notas del panel', 'Agregar nota de otro juez', initial => scoreRow([...known.values()], initial), slot.judges);
          fields.append(scores.node, el('p', { className: 'hint' }, 'Las notas del panel son mediciones. El juez que envía la fuente debe estar asignado al turno.'));
        }
        if (metrics.length) fields.append(el('fieldset', { className: 'section' }, el('legend', {}, 'Métricas del reglamento'), grid(...metrics)));
      }
      renderSource(); source.input.addEventListener('change', renderSource);
      form.addEventListener('submit', event => {
        event.preventDefault();
        submitAction(form, context, () => {
          const body = { challengeId: challenge.id, judgeId: judge.input.value, measurements: Object.fromEntries(metrics.map(item => [item.input.name, Number(item.input.value)])) };
          if (source.input.value === 'AUTOMATIC_MEASUREMENTS') fixed.forEach(item => { body[item.input.name] = Number(item.input.value); });
          else {
            const entries = scores.values();
            if (!entries.length) throw new Error('Agregá al menos una nota del panel.');
            if (new Set(entries.map(entry => entry[0])).size !== entries.length) throw new Error('Cada juez puede tener una sola nota en este envío.');
            body.scores = Object.fromEntries(entries);
          }
          busy = true; slotChoice.input.disabled = true; refresh.disabled = true;
          return api.receive(slot.slotId, source.input.value, body).finally(() => { busy = false; slotChoice.input.disabled = false; refresh.disabled = false; });
        }, async attempt => { await load(attempt.id); notify(`La fuente se guardó en el intento ${attempt.number}. El estado y el puntaje fueron consultados al servidor.`); });
      });
      content.append(panel(heading(pending ? `Completar el intento ${latest.number}` : 'Nuevo intento', teams.get(slot.teamId) || slot.teamId), form), ...results);
    } catch (error) { if (current()) { content.replaceChildren(empty('No se pudo consultar el turno. Volvé a consultar o recargá los datos.')); notify(error.message, error.details, true); } }
  }
  slotChoice.input.addEventListener('change', () => load());
  load();
}
