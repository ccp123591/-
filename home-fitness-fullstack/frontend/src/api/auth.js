import client from './client';

export const authApi = {
  loginByEmail:    (email, password) => client.post('/auth/login/email', { email, password }),
  loginByPhone:    (phone, code)     => client.post('/auth/login/phone', { phone, code }),
  loginAsGuest:    (deviceId)        => client.post('/auth/login/guest', { deviceId }),

  register:        (data)            => client.post('/auth/register', data),
  // 后端 SendCodeRequest 需要 { target, purpose }
  sendSmsCode:     (phone, purpose = 'login') => client.post('/auth/sms/send',   { target: phone, purpose }),
  sendEmailCode:   (email, purpose = 'login') => client.post('/auth/email/send', { target: email, purpose }),

  refresh:         (refreshToken)    => client.post('/auth/refresh', { refreshToken }),
  logout:          (refreshToken)    => client.post('/auth/logout', refreshToken ? { refreshToken } : {}),
  me:              ()                => client.get('/auth/me'),

  passwordForgot:  (email)           => client.post('/auth/password/forgot', { email }),
  passwordReset:   (token, newPassword) => client.post('/auth/password/reset', { token, newPassword })
};
