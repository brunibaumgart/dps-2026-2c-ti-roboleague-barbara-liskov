export class ApiError extends Error {
  constructor(status, body) {
    super(body?.error || `No se pudo completar la solicitud (${status}).`);
    this.status = status;
    this.details = Array.isArray(body?.details) ? body.details : [];
  }
}

export async function request(path, { method = 'GET', body, fetcher = globalThis.fetch } = {}) {
  let response;
  try {
    response = await fetcher(path, {
      method, headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch {
    throw new ApiError(0, { error: 'No hay conexión con el servidor. Verificá la conexión y recargá los datos.' });
  }
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : null; }
  catch { throw new ApiError(response.status, { error: 'El servidor devolvió una respuesta que no se puede leer.' }); }
  if (!response.ok) throw new ApiError(response.status, data);
  return data;
}

const id = encodeURIComponent;
export const api = {
  editions: () => request('/editions'),
  challenges: edition => request(`/editions/${id(edition)}/challenges`),
  registrations: edition => request(`/editions/${id(edition)}/registrations`),
  register: (edition, body) => request(`/editions/${id(edition)}/registrations`, { method: 'POST', body }),
  updateRegistration: (edition, team, body) => request(`/editions/${id(edition)}/registrations/${id(team)}`, { method: 'PUT', body }),
  rounds: (challenge, category) => request(`/challenges/${id(challenge)}/rounds${category === undefined ? '' : `?categoryId=${id(category)}`}`),
  schedule: (challenge, body) => request(`/challenges/${id(challenge)}/rounds`, { method: 'POST', body }),
  rulebook: (challenge, version) => request(`/challenges/${id(challenge)}/rulebook/versions/${version}`),
  breakdown: attempt => request(`/attempts/${id(attempt)}/breakdown`),
  receive: (attempt, source, body) => request(`/attempts/${id(attempt)}/${source === 'AUTOMATIC_MEASUREMENTS' ? 'measurements' : 'judge-scores'}`, { method: 'PUT', body }),
  appeals: (challenge, category) => request(`/challenges/${id(challenge)}/appeals?categoryId=${id(category)}`),
  attemptAppeals: attempt => request(`/attempts/${id(attempt)}/appeals`),
  fileAppeal: (attempt, body) => request(`/attempts/${id(attempt)}/appeals`, { method: 'POST', body }),
  reviewAppeal: (appealId, body) => request(`/appeals/${id(appealId)}/review`, { method: 'POST', body }),
  acceptAppeal: (appealId, body) => request(`/appeals/${id(appealId)}/acceptance`, { method: 'POST', body }),
  rejectAppeal: (appealId, body) => request(`/appeals/${id(appealId)}/rejection`, { method: 'POST', body }),
  standings: (challenge, category) => request(`/challenges/${id(challenge)}/standings?categoryId=${id(category)}`),
  standingsVersion: (challenge, category, version) => request(`/challenges/${id(challenge)}/standings/versions/${version}?categoryId=${id(category)}`),
  recalculateStandings: (challenge, category) => request(`/challenges/${id(challenge)}/standings/versions?categoryId=${id(category)}`, { method: 'POST' }),
  publishStandings: (challenge, category, version, body) => request(`/challenges/${id(challenge)}/standings/versions/${version}/publication?categoryId=${id(category)}`, { method: 'POST', body }),
};
