-- V8 — 情感分析记录。补齐 EmotionRecord 实体的生产 MySQL schema。
CREATE TABLE IF NOT EXISTS t_emotion_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  source VARCHAR(32) NOT NULL,
  ref_id BIGINT NULL,
  text VARCHAR(1000) NULL,
  emotion VARCHAR(16) NOT NULL,
  score DOUBLE NOT NULL,
  tags VARCHAR(256) NULL,
  provider VARCHAR(32) NULL,
  created_at DATETIME(6) NULL,
  INDEX idx_emotion_user_created (user_id, created_at),
  INDEX idx_emotion_source (source, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
