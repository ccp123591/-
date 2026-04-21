import client from './client';

export const sessionApi = {
  create:       (data)      => client.post('/sessions', data),
  list:         (params)    => client.get('/sessions', { params }),
  detail:       (id)        => client.get(`/sessions/${id}`),
  update:       (id, data)  => client.put(`/sessions/${id}`, data),
  remove:       (id)        => client.delete(`/sessions/${id}`),
  batchSync:    (list)      => client.post('/sessions/batch', { sessions: list }),
  exportCsv:    ()          => client.get('/sessions/export/csv', { responseType: 'blob' })
};
