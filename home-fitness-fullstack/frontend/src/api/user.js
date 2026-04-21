import client from './client';

export const userApi = {
  getProfile:  ()         => client.get('/users/me'),
  updateProfile: (data)   => client.put('/users/me', data),
  updateAvatar: (file)    => {
    const fd = new FormData();
    fd.append('avatar', file);
    return client.post('/users/me/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  getStats:    ()         => client.get('/users/me/stats'),
  getCalendar: (yearMonth)=> client.get('/users/me/calendar', { params: { yearMonth } }),
  follow:      (userId)   => client.post(`/users/${userId}/follow`),
  unfollow:    (userId)   => client.delete(`/users/${userId}/follow`),
  followers:   (userId)   => client.get(`/users/${userId}/followers`),
  followings:  (userId)   => client.get(`/users/${userId}/followings`)
};
