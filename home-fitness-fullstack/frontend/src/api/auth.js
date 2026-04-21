import client from './client';

export const authApi = {
  loginByEmail:    (email, password) => client.post('/auth/login/email', { email, password }),
  loginByPhone:    (phone, code)     => client.post('/auth/login/phone', { phone, code }),
  loginByWechat:   (code)            => client.post('/auth/login/wechat', { code }),
  loginAsGuest:    (deviceId)        => client.post('/auth/login/guest', { deviceId }),

  register:        (data)            => client.post('/auth/register', data),
  sendSmsCode:     (phone)           => client.post('/auth/sms/send', { phone }),
  sendEmailCode:   (email)           => client.post('/auth/email/send', { email }),

  refresh:         (refreshToken)    => client.post('/auth/refresh', { refreshToken }),
  logout:          ()                => client.post('/auth/logout'),
  me:              ()                => client.get('/auth/me')
};
