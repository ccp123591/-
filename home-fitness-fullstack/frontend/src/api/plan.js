import client from './client';

export const planApi = {
  list:          (params)     => client.get('/plans', { params }),
  detail:        (id)         => client.get(`/plans/${id}`),
  create:        (data)       => client.post('/plans', data),
  update:        (id, data)   => client.put(`/plans/${id}`, data),
  remove:        (id)         => client.delete(`/plans/${id}`),

  myPlans:       ()           => client.get('/plans/mine'),
  adopt:         (id)         => client.post(`/plans/${id}/adopt`),
  abandon:       (id)         => client.delete(`/plans/${id}/adopt`),
  updateProgress:(id, data)   => client.put(`/plans/${id}/progress`, data),

  official:      ()           => client.get('/plans/official'),
  market:        (params)     => client.get('/plans/market', { params })
};
