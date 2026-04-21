import client from './client';

export const exerciseApi = {
  list:         ()        => client.get('/exercises'),
  detail:       (code)    => client.get(`/exercises/${code}`),
  create:       (data)    => client.post('/exercises', data),
  update:       (code, d) => client.put(`/exercises/${code}`, d),
  remove:       (code)    => client.delete(`/exercises/${code}`)
};

export const badgeApi = {
  all:          ()        => client.get('/badges'),
  mine:         ()        => client.get('/badges/mine'),
  check:        ()        => client.post('/badges/check')
};

export const adminApi = {
  dashboard:    ()        => client.get('/admin/dashboard'),
  users:        (params)  => client.get('/admin/users', { params }),
  banUser:      (id)      => client.post(`/admin/users/${id}/ban`),
  unbanUser:    (id)      => client.post(`/admin/users/${id}/unban`),
  sessions:     (params)  => client.get('/admin/sessions', { params }),
  analytics:    (params)  => client.get('/admin/analytics', { params })
};
