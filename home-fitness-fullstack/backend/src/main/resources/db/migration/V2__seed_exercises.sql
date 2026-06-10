-- V2 — 动作库种子（与 dev 的 data.sql 保持一致）
INSERT INTO t_exercise (code, name, description, kind, default_threshold_down, default_threshold_up, enabled, sort_order) VALUES
('squat',       '深蹲',     '锻炼下肢力量',       'rep', 90, 160, true, 1),
('stretch',     '前屈伸展', '提升柔韧性',         'rep', 60, 160, true, 2),
('pushup',      '俯卧撑',   '强化胸臂力量',       'rep', 80, 160, true, 3),
('lunge',       '弓步蹲',   '下肢稳定性训练',     'rep', 100, 170, true, 4),
('bridge',      '臀桥',     '臀部激活',           'rep', 150, 175, true, 5),
('plank',       '平板支撑', '核心力量',           'timed', 150, 170, true, 6),
('jumpingJack', '开合跳',   '有氧燃脂',           'rep', 40, 140, true, 7);
