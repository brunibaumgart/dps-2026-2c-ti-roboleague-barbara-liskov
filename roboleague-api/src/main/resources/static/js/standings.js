import { api } from './api.js';
import { formatTime, standingsLabels } from './models.js';
import { el, field, grid, panel, heading, table, empty, button, submitAction, notify } from './ui.js';

export function mountStandings(root, context) {
  const { challenge, category } = context.data;
  if (!challenge) { root.append(empty('Elegí un desafío para consultar su tabla.')); return; }
  const content = el('div');
  let requestVersion = 0;
  const refresh = button('Consultar tabla', () => load());
  const recalculate = button('Recalcular', () => submitAction(dummyForm(), context,
    () => api.recalculateStandings(challenge.id, category.id),
    async version => { await load(); notify(`Se calculó la versión ${version.number}. Sigue provisional hasta publicarla.`); }), 'primary');
  root.append(panel(heading('Tabla de posiciones', `${challenge.name} · ${category.name}. Cada recálculo agrega una versión; publicar solo puede hacerse sobre la última.`),
    el('div', { className: 'actions' }, refresh, recalculate)), content);

  async function load(selected) {
    const version = ++requestVersion;
    content.replaceChildren(empty('Consultando la tabla…'));
    const current = () => context.isCurrent() && requestVersion === version;
    try {
      const standings = await api.standings(challenge.id, category.id);
      if (!current()) return;
      content.replaceChildren();
      content.append(pendingPanel(standings.pending));
      if (!standings.latest) {
        content.append(empty('Todavía no hay una versión calculada. Usá «Recalcular» para generar la primera.'));
        return;
      }
      const shown = selected && selected !== standings.latest.number
        ? await api.standingsVersion(challenge.id, category.id, selected) : standings.latest;
      if (!current()) return;
      content.append(versionPanel(shown, shown.number === standings.latest.number ? 'Última versión' : `Versión ${shown.number}`));
      if (standings.official && standings.official.number !== shown.number) {
        content.append(versionPanel(standings.official, 'Versión oficial'));
      }
      content.append(historyPanel(standings, shown.number, load));
      if (standings.latest.status !== 'OFFICIAL') content.append(publishForm(challenge, category, standings, context, load));
    } catch (error) {
      if (current()) { content.replaceChildren(empty('No se pudo consultar la tabla. Volvé a consultar o recargá los datos.')); notify(error.message, error.details, true); }
    }
  }
  load();
}

function dummyForm() {
  const form = el('form');
  form.append(el('fieldset'));
  return form;
}

function pendingPanel(pending) {
  const items = [];
  if (pending.openAppeals) items.push(`${pending.openAppeals} apelación(es) abierta(s)`);
  if (pending.unfinishedTurns) items.push(`${pending.unfinishedTurns} turno(s) sin resultado`);
  if (pending.outdated) items.push('Los resultados cambiaron desde el último cálculo');
  return panel(heading('Qué bloquearía publicar ahora', 'Lo informa el servidor con el estado actual de la categoría.'),
    items.length ? el('ul', {}, items.map(item => el('li', {}, item))) : el('p', {}, 'Nada abierto: si la última versión coincide con los resultados, se puede publicar.'));
}

function versionPanel(version, title) {
  const publication = version.publication
    ? `Publicada ${formatTime(version.publication.publishedAt)} por ${version.publication.publishedBy}: ${version.publication.notes}`
    : 'Todavía no publicada';
  return panel(heading(title, `v${version.number} · ${standingsLabels[version.status] || version.status} · calculada ${formatTime(version.calculatedAt)} · ${publication}`),
    version.entries.length ? table(['Puesto', 'Equipo', 'Puntos', 'Por qué', 'Rondas que cuentan'], version.entries.map(entry => [
      entry.position, entry.teamName, entry.total, entry.explanation,
      entry.considered.map(round => `${round.attemptId} (${round.total})`).join(', ') || '—',
    ])) : empty('Esta versión no tiene filas.'));
}

function historyPanel(standings, selected, load) {
  return panel(heading('Versiones', 'Ninguna versión se edita. Publicar una reemplaza a la oficial anterior, que queda como reemplazada.'),
    table(['Versión', 'Estado', 'Calculada', ''], standings.versions.map(version => [
      `v${version.number}`, standingsLabels[version.status] || version.status, formatTime(version.calculatedAt),
      version.number === selected ? 'Vista' : button('Ver', () => load(version.number), 'link-button'),
    ])));
}

function publishForm(challenge, category, standings, context, reload) {
  const author = field('Quién publica', 'publishedBy');
  const notes = field('Notas', 'notes');
  const form = el('form', {}, el('fieldset', {}, grid(author, notes),
    el('p', { className: 'hint' }, `Se pedirá publicar la v${standings.latest.number}. Si hay apelaciones abiertas, turnos sin resultado o resultados más nuevos, el servidor responde 409 con cada motivo.`),
    el('div', { className: 'actions' }, el('button', { type: 'submit' }, `Publicar v${standings.latest.number}`))));
  form.addEventListener('submit', event => {
    event.preventDefault();
    submitAction(form, context, () => api.publishStandings(challenge.id, category.id, standings.latest.number, {
      publishedBy: author.input.value.trim(), notes: notes.input.value.trim(),
    }), async () => { await reload(); notify('La versión quedó oficial.'); });
  });
  return panel(heading('Publicar la última versión'), form);
}
