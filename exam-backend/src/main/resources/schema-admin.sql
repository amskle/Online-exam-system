CREATE TABLE IF NOT EXISTS subject (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(200),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS question (
  id INT PRIMARY KEY AUTO_INCREMENT,
  subject_id INT NOT NULL,
  subject_name VARCHAR(50) NOT NULL,
  type TINYINT NOT NULL COMMENT '1单选 2多选 3判断 4主观',
  difficulty TINYINT NOT NULL COMMENT '1简单 2中等 3困难',
  content TEXT NOT NULL,
  options TEXT,
  answer TEXT NOT NULL,
  analysis TEXT,
  score INT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_paper (
  id INT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(100) NOT NULL,
  subject_id INT NOT NULL,
  subject_name VARCHAR(50) NOT NULL,
  total_score INT NOT NULL,
  duration INT NOT NULL,
  max_attempts INT NOT NULL DEFAULT 1 COMMENT '考试次数限制',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0未发布 1已发布 2已结束',
  start_time DATETIME,
  end_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_paper_question (
  id INT PRIMARY KEY AUTO_INCREMENT,
  paper_id INT NOT NULL,
  question_id INT NOT NULL,
  paper_score INT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_record (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  username VARCHAR(50) NOT NULL,
  paper_id INT NOT NULL,
  paper_title VARCHAR(100) NOT NULL,
  score INT NOT NULL DEFAULT 0,
  total_score INT NOT NULL,
  pass_score INT NOT NULL DEFAULT 60,
  attempt_count INT NOT NULL DEFAULT 1 COMMENT '当前记录累计考试次数',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0进行中 1已交卷',
  start_time DATETIME,
  submit_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exam_record_answer (
  id INT PRIMARY KEY AUTO_INCREMENT,
  record_id INT NOT NULL,
  question_id INT NOT NULL,
  type TINYINT NOT NULL,
  question_content TEXT NOT NULL,
  options TEXT,
  user_answer TEXT,
  correct_answer TEXT,
  full_score INT NOT NULL,
  score INT NOT NULL DEFAULT 0,
  judgement VARCHAR(20),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wrong_question (
  id INT PRIMARY KEY AUTO_INCREMENT,
  user_id INT NOT NULL,
  question_id INT NOT NULL,
  subject_id INT,
  subject_name VARCHAR(50),
  type TINYINT NOT NULL,
  content TEXT NOT NULL,
  options TEXT,
  user_answer TEXT,
  correct_answer TEXT,
  analysis TEXT,
  wrong_count INT NOT NULL DEFAULT 1,
  mastered TINYINT(1) NOT NULL DEFAULT 0,
  last_wrong_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
