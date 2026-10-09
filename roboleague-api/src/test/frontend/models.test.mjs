import test from 'node:test';
import assert from 'node:assert/strict';
import { attemptId, metricsFor, teamCandidate, resourceSuggestions, slotsFor } from '../../main/resources/static/js/models.js';
import { ApiError, request } from '../../main/resources/static/js/api.js';

test('an attempt retains the full textual slot id and requires a positive integer', () => {
  assert.equal(attemptId('slot-with-hyphens', 2), 'slot-with-hyphens-2');
  for (const invalid of [0, -1, 1.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) assert.throws(() => attemptId('slot', invalid));
});
test('editing a response does not send read-only verification metadata', () => {
  const original = { id: 'team', name: '<img src=x>', institution: 'University', members: [{ id: 'member', fullName: 'Name', birthDate: '2010-01-01', role: 'LEADER' }],
    robot: { id: 'robot', name: 'Bot', weightGrams: 1000, lengthMm: 100, widthMm: 100, heightMm: 100, actuatorCount: 2, sensors: ['LIDAR'] },
    documentation: { documents: { consent: 'file.pdf' }, verified: true, verifiedAt: '2026-10-08T12:00:00', verifiedBy: 'actor', revocationReason: null } };
  const candidate = teamCandidate(original);
  assert.equal(candidate.id, 'team');
  assert.equal(candidate.name, '<img src=x>');
  assert.deepEqual(candidate.documentation, { documents: { consent: 'file.pdf' }, verifiedBy: 'actor' });
  candidate.robot.sensors.push('camera'); candidate.documentation.documents.consent = 'changed.pdf';
  assert.deepEqual(original.robot.sensors, ['LIDAR']);
  assert.equal(original.documentation.documents.consent, 'file.pdf');
});
test('metric declarations preserve their source, units and ranges', () => {
  const sheet = { metrics: [{ name: 'distance', source: 'AUTOMATIC_MEASUREMENTS', unit: 'METERS', range: { min: 0, max: 3 } },
    { name: 'rescued', source: 'JUDGE_PANEL', unit: 'COUNT', range: { min: 0, max: 4 } }] };
  assert.deepEqual(metricsFor(sheet, 'JUDGE_PANEL'), [sheet.metrics[1]]);
  assert.equal(metricsFor(sheet, 'AUTOMATIC_MEASUREMENTS')[0].unit, 'METERS');
});
test('resource suggestions keep identities and slot presentation preserves parallel times', () => {
  const rounds = [{ name: 'First', roundId: 'round', slots: [
    { slotId: 'B', startTime: '2026-11-10T10:00:00', track: { id: 'p2', name: 'Second', isActive: true }, judges: [{ id: 'j2', fullName: 'Judge 2' }] },
    { slotId: 'A', startTime: '2026-11-10T10:00:00', track: { id: 'p1', name: 'First', isActive: true }, judges: [{ id: 'j1', fullName: 'Judge 1' }] },
    { slotId: 'C', startTime: '2026-11-10T10:06:00', track: { id: 'p1', name: 'First', isActive: true }, judges: [{ id: 'j1', fullName: 'Judge 1' }] },
  ] }];
  assert.deepEqual(slotsFor(rounds).map(slot => slot.slotId), ['A', 'B', 'C']);
  assert.equal(slotsFor(rounds)[0].startTime, slotsFor(rounds)[1].startTime);
  assert.equal(resourceSuggestions(rounds).tracks.length, 2);
  assert.deepEqual(resourceSuggestions(rounds).judges.map(judge => judge.id), ['j2', 'j1']);
});
for (const status of [400, 409, 422]) {
  test(`HTTP ${status} retains every server reason and never retries a mutation`, async () => {
    let calls = 0;
    await assert.rejects(request('/endpoint', { method: 'POST', body: { name: 'Name' }, fetcher: async (path, options) => {
      calls++; assert.equal(options.method, 'POST'); assert.equal(options.body, '{"name":"Name"}');
      return { ok: false, status, text: async () => JSON.stringify({ error: 'Rejected', details: ['first', 'second'] }) };
    } }), error => error instanceof ApiError && error.status === status && error.message === 'Rejected' && error.details.join(',') === 'first,second');
    assert.equal(calls, 1);
  });
}
test('network and malformed responses become explicit errors rather than false success', async () => {
  await assert.rejects(request('/', { fetcher: async () => { throw new Error('offline'); } }), error => error.status === 0);
  await assert.rejects(request('/', { fetcher: async () => ({ ok: true, status: 200, text: async () => '<html>proxy error</html>' }) }), error => error instanceof ApiError);
});
