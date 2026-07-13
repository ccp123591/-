import client from './client';

/**
 * 训练安全告警：跌倒预警超时未确认 → 邮件通知紧急联系人（家属）。
 * dev 环境后端为日志 mock，生产替换 MailSender 实现即真发送。
 */
export const safetyApi = {
  fallAlert: (contactEmail, note) =>
    client.post('/safety/fall-alert', { contactEmail, note })
};
