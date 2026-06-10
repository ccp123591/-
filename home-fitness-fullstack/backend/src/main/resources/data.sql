-- FitCoach 初始种子数据（开发环境 H2 自动加载）
-- 注意：admin / demo 用户由 DataInitializer 启动时创建（避免硬编码 bcrypt hash 不匹配）

-- 动作库
INSERT INTO t_exercise (code, name, description, kind, default_threshold_down, default_threshold_up, enabled, sort_order) VALUES
('squat',       '深蹲',     '锻炼下肢力量',       'rep', 90, 160, true, 1),
('stretch',     '前屈伸展', '提升柔韧性',         'rep', 60, 160, true, 2),
('pushup',      '俯卧撑',   '强化胸臂力量',       'rep', 80, 160, true, 3),
('lunge',       '弓步蹲',   '下肢稳定性训练',     'rep', 100, 170, true, 4),
('bridge',      '臀桥',     '臀部激活',           'rep', 150, 175, true, 5),
('plank',       '平板支撑', '核心力量',           'timed', 150, 170, true, 6),
('jumpingJack', '开合跳',   '有氧燃脂',           'rep', 40, 140, true, 7);

-- 官方训练计划（dev 的 items_json 与 prod V3 一致，便于前端真实联调）
INSERT INTO t_plan (title, description, level, cover, days, official, published, author_id, adopt_count, created_at, items_json) VALUES
('新手入门 7 天', '零基础友好，每天 15 分钟', 'NEWBIE', '#00F0FF', 7, true, true, 1, 128, CURRENT_TIMESTAMP,
 '[{"day":1,"items":[{"action":"squat","reps":10},{"action":"stretch","reps":10}]},{"day":2,"items":[{"action":"pushup","reps":8},{"action":"bridge","reps":12}]},{"day":3,"items":[{"action":"jumpingJack","reps":20},{"action":"stretch","reps":10}]},{"day":4,"items":[{"action":"squat","reps":12},{"action":"plank","duration":30}]},{"day":5,"items":[{"action":"pushup","reps":10},{"action":"lunge","reps":10}]},{"day":6,"items":[{"action":"bridge","reps":15},{"action":"stretch","reps":12}]},{"day":7,"items":[{"action":"squat","reps":15},{"action":"plank","duration":45}]}]'),
('核心强化 14 天', '针对核心力量的系统训练', 'INTERMEDIATE', '#7C6AFF', 14, true, true, 1, 56, CURRENT_TIMESTAMP,
 '[{"day":1,"items":[{"action":"plank","duration":30}]},{"day":2,"items":[{"action":"plank","duration":35}]},{"day":3,"items":[{"action":"plank","duration":40}]},{"day":4,"items":[{"action":"plank","duration":45}]},{"day":5,"items":[{"action":"plank","duration":50}]},{"day":6,"items":[{"action":"plank","duration":55}]},{"day":7,"items":[{"action":"plank","duration":60}]}]'),
('30 天俯卧撑挑战', '从 10 个到 100 个的进阶', 'INTERMEDIATE', '#FF9F43', 30, true, true, 1, 82, CURRENT_TIMESTAMP,
 '[{"day":1,"items":[{"action":"pushup","reps":10}]},{"day":7,"items":[{"action":"pushup","reps":28}]},{"day":14,"items":[{"action":"pushup","reps":49}]},{"day":21,"items":[{"action":"pushup","reps":70}]},{"day":30,"items":[{"action":"pushup","reps":100}]}]'),
('柔韧性提升', '每天 20 分钟拉伸', 'NEWBIE', '#00E58A', 10, true, true, 1, 45, CURRENT_TIMESTAMP,
 '[{"day":1,"items":[{"action":"stretch","reps":10},{"action":"bridge","reps":10}]},{"day":5,"items":[{"action":"stretch","reps":18},{"action":"bridge","reps":18}]},{"day":10,"items":[{"action":"stretch","reps":30},{"action":"bridge","reps":30}]}]');

-- 徽章
INSERT INTO t_badge (code, name, description, icon, sort_order, criteria_json) VALUES
('first_training', '初次训练', '完成第一次训练',     '🎯', 1, '{"sessions":1}'),
('hundred_reps',   '百次达成', '累计完成 100 次',    '💯', 2, '{"totalReps":100}'),
('seven_streak',   '七日连续', '连续打卡 7 天',      '🔥', 3, '{"streakDays":7}'),
('perfect_score',  '完美评分', '单次评分 95+',       '⭐', 4, '{"bestScore":95}'),
('rhythm_master',  '节奏大师', '节奏评分 100',       '🎵', 5, '{"bestRhythm":100}'),
('thirty_days',    '坚持不懈', '累计 30 天',         '💪', 6, '{"totalDays":30}');

-- 挑战赛（dev 种子 — 时间窗覆盖全年，保证联调期间始终活跃）
INSERT INTO t_challenge (title, description, action, target_reps, start_date, end_date, status, cover, created_at) VALUES
('年度深蹲挑战 1000 次', '累计完成 1000 次深蹲，证明你的下肢力量', 'squat',  1000, '2026-01-01', '2026-12-31', 'ACTIVE', '#c96442', CURRENT_TIMESTAMP),
('俯卧撑达人 300 次',     '累计完成 300 次俯卧撑，强化上肢',       'pushup', 300,  '2026-01-01', '2026-12-31', 'ACTIVE', '#6a9bcc', CURRENT_TIMESTAMP),
('平板支撑铁核心 600 秒', '累计坚持 600 秒平板支撑，打造钢铁核心', 'plank',  600,  '2026-01-01', '2026-12-31', 'ACTIVE', '#788c5d', CURRENT_TIMESTAMP);
