import client from './client';

export const socialApi = {
  // 动态
  feed:          (params)     => client.get('/posts/feed', { params }),
  post:          (data)       => client.post('/posts', data),
  postDetail:    (id)         => client.get(`/posts/${id}`),
  deletePost:    (id)         => client.delete(`/posts/${id}`),
  like:          (id)         => client.post(`/posts/${id}/like`),
  unlike:        (id)         => client.delete(`/posts/${id}/like`),
  comment:       (id, content)=> client.post(`/posts/${id}/comments`, { content }),
  comments:      (id)         => client.get(`/posts/${id}/comments`),

  // 排行榜
  leaderboardWeekly: ()       => client.get('/leaderboard/weekly'),
  leaderboardMonthly:()       => client.get('/leaderboard/monthly'),
  leaderboardFriends: ()      => client.get('/leaderboard/friends'),

  // 挑战赛
  challenges:    ()           => client.get('/challenges'),
  joinChallenge: (id)         => client.post(`/challenges/${id}/join`),
  challengeRank: (id)         => client.get(`/challenges/${id}/rank`)
};
