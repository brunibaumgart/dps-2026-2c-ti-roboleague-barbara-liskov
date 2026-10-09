import { api } from './api.js';
import { mountRegistration } from './registration.js';
import { mountScheduling } from './scheduling.js';
import { mountResults } from './results.js';
import { el, panel, heading, empty, notify } from './ui.js';

const views = {
  registration: ['Equipos', 'Inscripciones y datos de los equipos participantes.'],
  scheduling: ['Rondas y turnos', 'Organizá los recursos y consultá los horarios de competencia.'],
  results: ['Cargar resultados', 'Capturá las fuentes requeridas por el reglamento de cada desafío.'],
  breakdown: ['Desglose e historial', 'Puntajes, fuentes y revisiones confirmados por el servidor.'],
  appeals: ['Apelaciones', 'Presentación, revisión y resolución de reclamos.'],
  standings: ['Tabla de posiciones', 'Clasificación y versiones oficiales de la competencia.'],
};
const root = document.querySelector('#workspace');
const editionSelect = document.querySelector('#edition');
const challengeSelect = document.querySelector('#challenge');
const categorySelect = document.querySelector('#category');
const refresh = document.querySelector('#refresh');
let editions = [], data, view = 'registration', selectedSlot, attemptNumber = 1, epoch = 0, busy = false, reloadRequired = false;
const menuToggle = document.querySelector('#menu-toggle');
const secondaryMenu = document.querySelector('#secondary-menu');
function setMenuOpen(open, restoreFocus = true) {
  secondaryMenu.hidden = !open;
  document.querySelector('.app-shell').classList.toggle('menu-open', open);
  menuToggle.setAttribute('aria-expanded', String(open));
  menuToggle.setAttribute('aria-label', open ? 'Cerrar menú de competencia' : 'Abrir menú de competencia');
  if (!open && restoreFocus) menuToggle.focus({ preventScroll: true });
}
menuToggle.addEventListener('click', () => setMenuOpen(secondaryMenu.hidden));
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && !secondaryMenu.hidden) {
    event.preventDefault();
    setMenuOpen(false);
  }
});

function options(select, items, label, selected, placeholder) {
  select.replaceChildren(...items.map(item => el('option', { value: item.id }, label(item))));
  if (!items.length) select.append(el('option', { value: '' }, placeholder));
  if (items.some(item => item.id === selected)) select.value = selected;
}
function setBusy(value) {
  busy = value;
  refresh.disabled = value;
  editionSelect.disabled = value || reloadRequired || !editions.length;
  challengeSelect.disabled = value || reloadRequired || !data?.challenges.length;
  categorySelect.disabled = value || reloadRequired || !data?.edition.categoryDetails.length;
  document.querySelectorAll('button[data-view]').forEach(button => { button.disabled = value || reloadRequired; });
  root.setAttribute('aria-busy', String(value));
}
function render() {
  root.replaceChildren();
  document.querySelector('#page-title').textContent = views[view][0];
  document.querySelector('#page-description').textContent = views[view][1];
  document.querySelectorAll('button[data-view]').forEach(button => {
    if (button.dataset.view === view) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  if (!data?.edition) { root.append(empty('Todavía no hay una edición configurada para esta competencia.')); return; }
  const currentEpoch = epoch;
  const context = { data, selectedSlot, attemptNumber, isCurrent: () => currentEpoch === epoch, setBusy, canMutate: () => !reloadRequired,
    requireReload: () => { reloadRequired = true; },
    reload: loadContext, openSlot: (slotId, number = 1) => { selectedSlot = slotId; attemptNumber = number; changeView('results'); } };
  if (view === 'appeals' || view === 'standings') {
    const appeals = view === 'appeals';
    root.append(panel(heading(appeals ? 'Apelaciones todavía no disponibles' : 'Tabla todavía no disponible'),
      el('p', {}, appeals ? 'Faltan las operaciones del servidor para listar, presentar y resolver apelaciones. La revisión existe, pero no hay una consulta para seleccionar un reclamo.'
        : 'El servidor todavía no expone consultas de tabla, versiones, recálculo ni publicación.'),
      el('p', {}, 'Podés seguir trabajando con los equipos, los turnos y los resultados disponibles.'),
      el('span', { className: 'badge' }, 'Integración pendiente'))); return;
  }
  if (!data.category) { root.append(empty('Esta edición no tiene categorías configuradas.')); return; }
  if (view === 'registration') mountRegistration(root, context);
  else if (view === 'scheduling') mountScheduling(root, context);
  else mountResults(root, context, view === 'breakdown');
}
function changeView(next) {
  if (busy || reloadRequired) return;
  setMenuOpen(false, false);
  epoch++; view = next; render();
  root.focus({ preventScroll: true });
  window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
}
async function loadContext(overrides = {}) {
  const currentEpoch = ++epoch;
  const editionId = overrides.editionId || editionSelect.value;
  const categoryId = overrides.categoryId || categorySelect.value;
  const challengeId = overrides.challengeId || challengeSelect.value;
  setBusy(true); root.replaceChildren(empty('Consultando los datos de la competencia…'));
  try {
    editions = await api.editions();
    if (currentEpoch !== epoch) return;
    options(editionSelect, editions, item => item.name, editionId, 'Sin ediciones');
    const edition = editions.find(item => item.id === editionSelect.value);
    if (!edition) { reloadRequired = false; data = undefined; options(challengeSelect, [], item => item.name, '', 'Sin desafíos'); options(categorySelect, [], item => item.name, '', 'Sin categorías'); render(); return; }
    const [challenges, registrations] = await Promise.all([api.challenges(edition.id), api.registrations(edition.id)]);
    if (currentEpoch !== epoch) return;
    options(challengeSelect, challenges, item => item.name, challengeId, 'Sin desafíos');
    options(categorySelect, edition.categoryDetails, item => item.name, categoryId, 'Sin categorías');
    const resourceRounds = (await Promise.all(challenges.map(item => api.rounds(item.id)))).flat();
    if (currentEpoch !== epoch) return;
    const challenge = challenges.find(item => item.id === challengeSelect.value);
    const category = edition.categoryDetails.find(item => item.id === categorySelect.value);
    const rounds = resourceRounds.filter(item => item.challengeId === challenge?.id && item.categoryId === category?.id);
    data = { editions, edition, challenges, challenge, category, registrations, rounds, resourceRounds };
    reloadRequired = false;
    document.querySelector('#notice').hidden = true;
    render();
  } catch (error) {
    if (currentEpoch !== epoch) return;
    data = undefined;
    root.replaceChildren(empty('No se pudieron cargar los datos. Usá «Recargar datos» para volver a consultar.'));
    notify(error.message, error.details, true);
  } finally { if (currentEpoch === epoch) setBusy(false); }
}
editionSelect.addEventListener('change', () => { selectedSlot = undefined; attemptNumber = 1; loadContext(); });
challengeSelect.addEventListener('change', () => { selectedSlot = undefined; attemptNumber = 1; loadContext(); });
categorySelect.addEventListener('change', () => { selectedSlot = undefined; attemptNumber = 1; loadContext(); });
refresh.addEventListener('click', () => loadContext());
document.querySelectorAll('button[data-view]').forEach(button => button.addEventListener('click', () => changeView(button.dataset.view)));
loadContext();
