import client from './client';

/**
 * AI 教练：基于训练数据生成个性化反馈
 * 后端会调用 Claude API（当前接口为占位）
 */
export const coachApi = {
  // 训练结束后获取 AI 反馈
  feedback: (sessionId) => client.post(`/coach/feedback`, { sessionId }),
  // 获取训练建议（基于近 7 次训练）
  suggestion: () => client.get('/coach/suggestion'),
  // 生成本周训练计划
  weeklyPlan: () => client.get('/coach/weekly-plan'),
  // 历史 AI 反馈列表
  history: (params) => client.get('/coach/history', { params })
};
