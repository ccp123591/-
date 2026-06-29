import client from './client';

/**
 * AI 教练：基于训练数据生成个性化反馈，以及陪伴聊天 + 记忆唤起。
 */
export const coachApi = {
  /** 训练后反馈；formReview 为本次动作的视觉点评摘要（JoyAI-VL，可空）。 */
  feedback:   (sessionId, formReview = null) =>
    client.post('/coach/feedback', formReview ? { sessionId, formReview } : { sessionId }),
  suggestion: ()          => client.get('/coach/suggestion'),
  weeklyPlan: ()          => client.get('/coach/weekly-plan'),
  history:    (params)    => client.get('/coach/history', { params }),

  /**
   * 陪伴聊天（多轮 + RAG 记忆）。
   * @param {string} message  本次用户输入
   * @param {Array<{role:'user'|'assistant', content:string}>} history 最近若干轮，可空
   * @returns {{reply, provider, tokensUsed, recalled[]}}
   */
  chat: (message, history = []) => client.post('/coach/chat', { message, history }),

  /** 叙旧 — 按时间近的最近聊天记忆做老朋友式回顾 */
  reminisce: () => client.post('/coach/reminisce'),

  /**
   * 动作视觉点评 — 上传 1-3 帧训练画面，JoyAI-VL 给动作反馈。
   * @param {string} action 动作 code（squat/pushup…）
   * @param {number|null} reps 本组次数/秒数
   * @param {number|null} score 本组综合分
   * @param {File[]} files 1-3 帧 jpg
   * @returns {{action, summary, issues[], tips[], formScore, model}}
   */
  formCritique: (action, reps, score, files) => {
    const fd = new FormData();
    fd.append('action', action);
    if (reps != null) fd.append('reps', reps);
    if (score != null) fd.append('score', score);
    files.forEach(f => fd.append('frames', f));
    return client.post('/coach/form-critique', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 45000  // VLM 推理可能稍慢
    });
  }
};
