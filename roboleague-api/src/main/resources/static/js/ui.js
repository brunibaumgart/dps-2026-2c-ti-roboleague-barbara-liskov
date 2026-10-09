let sequence = 0;
export function el(tag, attributes = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attributes)) {
    if (key.startsWith('on') && typeof value === 'function') node.addEventListener(key.slice(2).toLowerCase(), value);
    else if (key === 'className') node.className = value;
    else if (key === 'value') node.value = value;
    else if (key === 'checked') node.checked = value;
    else if (value !== false && value !== undefined && value !== null) node.setAttribute(key, value === true ? '' : String(value));
  }
  for (const child of children.flat(Infinity)) if (child !== null && child !== undefined) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  return node;
}
export function field(label, name, { value = '', type = 'text', ...attributes } = {}) {
  const input = el('input', { id: `field-${++sequence}`, name, type, required: true, ...attributes, value });
  return { input, node: el('label', { for: input.id }, label, input) };
}
export function choice(label, name, options, value) {
  const input = el('select', { id: `field-${++sequence}`, name, required: true }, options.map(option => el('option', { value: option.value }, option.label)));
  if (value !== undefined) input.value = value;
  return { input, node: el('label', { for: input.id }, label, input) };
}
export function grid(...children) { return el('div', { className: 'grid' }, children.map(child => child.node || child)); }
export function heading(title, description, ...actions) { return el('div', { className: 'panel-heading' }, el('div', {}, el('h2', {}, title), description ? el('p', {}, description) : null), ...actions); }
export function panel(...children) { return el('section', { className: 'panel' }, children); }
export function button(label, action, style = 'secondary') { return el('button', { type: 'button', className: style, onClick: action }, label); }
export function empty(message) { return el('p', { className: 'empty' }, message); }
export function table(headers, rows) { return el('div', { className: 'table-wrap' }, el('table', {}, el('thead', {}, el('tr', {}, headers.map(header => el('th', { scope: 'col' }, header)))), el('tbody', {}, rows.map(row => el('tr', {}, row.map(cell => el('td', {}, cell))))))); }
export function notify(message, details = [], isError = false) {
  const notice = document.querySelector('#notice');
  notice.className = `notice${isError ? ' error' : ''}`;
  notice.setAttribute('role', isError ? 'alert' : 'status');
  notice.replaceChildren(el('strong', {}, message), ...(details.length ? [el('ul', {}, details.map(detail => el('li', {}, detail)))] : []));
  notice.hidden = false;
}
export async function submitAction(form, context, action, success) {
  if (form.dataset.pending || form.dataset.conflict || !context.canMutate() || !form.reportValidity()) return;
  form.dataset.pending = 'true';
  const controls = [...form.querySelectorAll('fieldset')].filter(item => !item.parentElement.closest('fieldset'));
  controls.forEach(item => { item.disabled = true; });
  context.setBusy(true);
  try {
    const result = await action();
    if (!context.isCurrent()) return;
    await success(result);
  } catch (error) {
    if (!context.isCurrent()) return;
    notify(error.message, [...(error.details || []), ...(error.status === 409 ? ['Recargá los datos antes de volver a enviar. Tu solicitud no se reintenta automáticamente.'] : [])], true);
    if (error.status === 409) { form.dataset.conflict = 'true'; context.requireReload(); }
  } finally {
    delete form.dataset.pending;
    controls.forEach(item => { item.disabled = !!form.dataset.conflict; });
    context.setBusy(false);
  }
}
export function collection(title, addLabel, buildRow, initial) {
  const list = el('div');
  const rows = [];
  function add(data) {
    const row = buildRow(data);
    const remove = button('Quitar', () => { row.node.remove(); rows.splice(rows.indexOf(row), 1); }, 'link-button');
    row.node.append(remove);
    rows.push(row); list.append(row.node);
  }
  initial.forEach(add);
  return { node: el('fieldset', { className: 'section' }, el('legend', {}, title), list, button(addLabel, () => add())), values: () => rows.map(row => row.value()), rows };
}
