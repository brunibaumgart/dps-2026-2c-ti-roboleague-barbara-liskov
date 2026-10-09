import { api } from './api.js';
import { teamCandidate } from './models.js';
import { el, field, choice, grid, panel, heading, table, empty, button, collection, submitAction, notify } from './ui.js';

const identity = () => crypto.randomUUID();
function memberRow(member = { id: identity() }) {
  const fullName = field('Nombre completo', 'fullName', { value: member.fullName });
  const birthDate = field('Fecha de nacimiento', 'birthDate', { type: 'date', value: member.birthDate });
  const role = field('Rol en el equipo', 'role', { value: member.role || 'MEMBER' });
  return { node: el('div', { className: 'row-card' }, grid(fullName, birthDate, role)),
    value: () => ({ id: member.id, fullName: fullName.input.value.trim(), birthDate: birthDate.input.value, role: role.input.value.trim() }) };
}
function documentRow(entry = ['', '']) {
  const type = field('Tipo de documento', 'documentType', { value: entry[0], placeholder: 'Consentimiento' });
  const reference = field('Referencia del documento', 'documentReference', { value: entry[1], placeholder: 'Archivo o enlace' });
  return { node: el('div', { className: 'row-card' }, grid(type, reference)), value: () => [type.input.value.trim(), reference.input.value.trim()] };
}
function limits(category) {
  return `${category.minMembers}–${category.maxMembers} integrantes · ${category.minAge}–${category.maxAge} años al inicio de la edición · Hasta ${category.maxWeightGrams} g · ${category.maxLengthMm} × ${category.maxWidthMm} × ${category.maxHeightMm} mm`;
}
export function mountRegistration(root, context) {
  const { edition, category, registrations } = context.data;
  const candidates = registrations.filter(registration => registration.categoryId === category.id);
  const editor = panel();
  const list = panel(heading('Equipos inscriptos', `${category.name} · ${candidates.length} equipo(s)`, button('Nuevo equipo', () => edit())));
  function edit(registration) {
    const team = registration ? teamCandidate(registration.team) : { id: identity(), robot: { id: identity(), sensors: [] }, documentation: { documents: {} }, members: [] };
    const name = field('Nombre del equipo', 'teamName', { value: team.name });
    const institution = field('Institución', 'institution', { value: team.institution, required: false });
    const categoryChoice = choice('Categoría de inscripción', 'enrollmentCategory', edition.categoryDetails.map(item => ({ value: item.id, label: item.name })), registration?.categoryId || category.id);
    const restriction = el('p', { className: 'limits' }, limits(edition.categoryDetails.find(item => item.id === categoryChoice.input.value)));
    categoryChoice.input.addEventListener('change', () => { restriction.textContent = limits(edition.categoryDetails.find(item => item.id === categoryChoice.input.value)); });
    const members = collection('Integrantes', 'Agregar integrante', memberRow, team.members.length ? team.members : [undefined, undefined]);
    const robotName = field('Nombre del robot', 'robotName', { value: team.robot.name });
    const robotFields = ['weightGrams', 'lengthMm', 'widthMm', 'heightMm', 'actuatorCount'].map((key, index) => field(
      ['Peso (g)', 'Largo (mm)', 'Ancho (mm)', 'Alto (mm)', 'Actuadores'][index], key,
      { type: 'number', min: key === 'actuatorCount' ? 0 : 0.001, step: key === 'actuatorCount' ? 1 : 'any', value: team.robot[key] }));
    const sensors = field('Sensores (separados por coma)', 'sensors', { value: team.robot.sensors.join(', '), required: false, placeholder: 'LIDAR, cámara' });
    const documents = collection('Documentación', 'Agregar documento', documentRow, Object.entries(team.documentation.documents).length ? Object.entries(team.documentation.documents) : [undefined]);
    const verifier = field('Responsable de la verificación', 'verifiedBy', { value: team.documentation.verifiedBy, required: false, placeholder: 'Nombre o referencia del responsable' });
    const form = el('form');
    const body = el('fieldset', {}, grid(name, institution), categoryChoice.node, restriction, members.node,
      el('fieldset', { className: 'section' }, el('legend', {}, 'Robot'), grid(robotName, ...robotFields, sensors)),
      documents.node, verifier.node, el('p', { className: 'hint' }, 'Al guardar se verifica el conjunto recibido a nombre del responsable indicado. Sin responsable, la documentación queda sin verificar. Usá referencias a documentos ya almacenados.'),
      el('div', { className: 'actions' }, el('button', { type: 'submit' }, registration ? 'Guardar cambios' : 'Inscribir equipo'), registration ? button('Cancelar edición', () => edit()) : null));
    form.append(body);
    form.addEventListener('submit', event => {
      event.preventDefault();
      submitAction(form, context, async () => {
        const entries = documents.values();
        if (new Set(entries.map(entry => entry[0])).size !== entries.length) throw new Error('Usá un tipo distinto para cada documento.');
        const robot = { id: team.robot.id, name: robotName.input.value.trim(), sensors: sensors.input.value.split(',').map(value => value.trim()).filter(Boolean) };
        robotFields.forEach(item => { robot[item.input.name] = Number(item.input.value); });
        const body = { categoryId: categoryChoice.input.value, team: { id: team.id, name: name.input.value.trim(), institution: institution.input.value,
          members: members.values(), robot, documentation: { documents: Object.fromEntries(entries), verifiedBy: verifier.input.value.trim() || null } } };
        return registration ? api.updateRegistration(edition.id, team.id, body) : api.register(edition.id, body);
      }, async result => { await context.reload({ categoryId: result.categoryId }); notify(registration ? 'Los cambios del equipo se guardaron.' : 'El equipo quedó inscripto.'); });
    });
    editor.replaceChildren(heading(registration ? `Editar ${registration.team.name}` : 'Inscribir un equipo', 'Los cambios se confirman cuando el equipo cumple los requisitos de inscripción.'), form);
  }
  list.append(candidates.length ? table(['Equipo', 'Institución', 'Integrantes', 'Documentación', ''], candidates.map(registration => [
    registration.team.name, registration.team.institution || '—', registration.team.members.length,
    registration.team.documentation.verified ? 'Verificada' : 'Sin verificar', button('Editar', () => edit(registration), 'link-button'),
  ])) : empty('Todavía no hay equipos inscriptos en esta categoría.'));
  root.append(list, editor); edit();
  const existing = panel(heading('Inscribir un equipo existente', 'Usá el estado actual de un equipo de otra edición.'));
  const load = button('Buscar equipos de otras ediciones', async () => {
    load.disabled = true;
    try {
      const other = await Promise.all(context.data.editions.filter(item => item.id !== edition.id).map(item => api.registrations(item.id)));
      if (!context.isCurrent()) return;
      const unique = new Map();
      for (const item of other.flat()) if (!registrations.some(current => current.teamId === item.teamId)) unique.set(item.teamId, item.team);
      if (!unique.size) { existing.replaceChildren(heading('Equipos de otras ediciones'), empty('No hay equipos disponibles para inscribir.')); return; }
      const team = choice('Equipo existente', 'existingTeam', [...unique.values()].map(item => ({ value: item.id, label: `${item.name} · ${item.institution || 'Sin institución'}` })));
      const form = el('form', {}, el('fieldset', {}, team.node, el('div', { className: 'actions' }, el('button', { type: 'submit' }, 'Inscribir en esta categoría'))));
      form.addEventListener('submit', event => { event.preventDefault(); submitAction(form, context, () => api.register(edition.id, { categoryId: category.id, teamId: team.input.value }), async () => { await context.reload(); notify('El equipo existente quedó inscripto.'); }); });
      existing.replaceChildren(heading('Inscribir un equipo existente'), form);
    } catch (error) { if (context.isCurrent()) notify(error.message, error.details, true); }
    finally { load.disabled = false; }
  });
  existing.append(load); root.append(existing);
}
