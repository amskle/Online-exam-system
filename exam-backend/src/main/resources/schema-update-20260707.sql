ALTER TABLE exam_paper
  ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 COMMENT '考试次数限制' AFTER duration;

ALTER TABLE exam_record
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 1 COMMENT '当前记录累计考试次数' AFTER pass_score;
