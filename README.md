# 在线考试系统

一个基于 Vue 3 + Spring Boot 的在线考试系统，支持管理员、老师、学生三类角色。系统包含用户与权限管理、题库管理、试卷管理、自动组卷、在线考试、考试记录、主观题批改、错题集、仪表盘统计等功能。

## 技术栈

前端：

- Vue 3
- TypeScript
- Vite
- Vue Router
- Element Plus
- Axios
- ECharts

后端：

- Spring Boot 3.2
- Java 21
- MyBatis-Plus
- MySQL
- JWT
- Maven

## 项目结构

```text
Online-exam-system/
├── exam-backend/              # Spring Boot 后端
│   ├── scripts/               # 数据生成脚本
│   └── src/main/resources/
│       ├── schema-admin.sql   # 数据库表结构初始化
│       ├── data-408.sql       # 408 真题题库种子数据
│       └── data-408-report.json
├── exam-frontend/             # Vue 前端
└── README.md
```

## 功能模块

### 管理员端

- 仪表盘：系统统计、趋势图表、题型/科目分布
- 用户管理：学生/老师账号管理、状态封禁、头像显示、权限展示
- 管理员管理：管理员账号增删改查
- 科目管理：考试科目增删改查
- 题目管理：单选、多选、判断、主观题管理
- 试卷管理：试卷增删改查、题目选择、自动组卷、考试次数限制
- 考试记录管理：记录查看、主观题批改、记录删除

### 老师端

老师通过 JWT 权限控制进入后台，只能使用：

- 题目管理
- 试卷管理
- 考试记录管理
- 个人信息修改

### 学生端

- 考试列表：查看已发布试卷，开始考试
- 在线考试：倒计时、答题卡、自动交卷、离开页面提醒
- 考试记录：查看本人考试记录与答题详情
- 错题集：查看错题、标记掌握、删除错题
- 个人信息修改

## 权限说明

系统角色值：

```text
1 = 学生
2 = 老师
3 = 管理员
```

前端路由守卫会根据 token 和角色判断访问权限。无权限访问会跳转到 `/401`，未知路径会进入 `/404`。

## 环境要求

- Node.js 18+
- Java 21
- Maven 3.8+
- MySQL 8.x

## 数据库配置

默认数据库配置在：

[exam-backend/src/main/resources/application.yml](exam-backend/src/main/resources/application.yml)

默认连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/exam?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: root
    password: 327510
```

首次运行前创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS exam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

后端启动时会自动执行：

- `schema-admin.sql`：创建后台、题库、试卷、考试记录、错题等表
- `DatabaseMigrationRunner`：补充旧库缺失字段
- `data-408.sql`：首次导入 408 真题题库数据

408 题库导入会记录到 `data_seed_log` 表，避免每次重启重复导入。

## 408 真题数据

项目已从 `A:/408-master/408-master` 的 408 真题 PDF 中生成题库种子数据：

- 科目：`408计算机学科专业基础`
- 选择题：480 道
- 综合/主观题：79 道
- 合计：559 道

相关文件：

- [data-408.sql](exam-backend/src/main/resources/data-408.sql)
- [data-408-report.json](exam-backend/src/main/resources/data-408-report.json)
- [generate_408_seed.py](exam-backend/scripts/generate_408_seed.py)

说明：`2016408.pdf` 文本层几乎为空，自动工具无法稳定抽取，因此该年份暂未导入。

## 启动后端

进入后端目录：

```bash
cd exam-backend
```

编译：

```bash
mvn -q -DskipTests compile
```

启动：

```bash
mvn spring-boot:run
```

后端默认端口：

```text
http://localhost:8077
```

## 启动前端

进入前端目录：

```bash
cd exam-frontend
```

安装依赖：

```bash
npm install
```

开发启动：

```bash
npm run dev -- --host 0.0.0.0 --port 8076
```

前端默认访问：

```text
http://localhost:8076
```

生产构建：

```bash
npm run build
```

## 常用入口

```text
/                         登录页
/admin-home/dashboards    管理员仪表盘
/admin-home/questions     老师题目管理入口
/user-home/dashboards     学生考试列表
/exam/:id                 在线考试页
/401                      未授权页
/404                      页面不存在页
```

登录后会根据角色自动跳转到对应端：

- 管理员：`/admin-home/dashboards`
- 老师：`/admin-home/questions`
- 学生：`/user-home/dashboards`

## 关键业务规则

- 学生和老师不能访问管理员专属模块。
- 老师只能访问题目、试卷、考试记录管理。
- 被封禁账号不能登录。
- 学生达到试卷考试次数上限后不能再次进入考试。
- 学生重新考试同一试卷时，会覆盖上一条考试记录和答题明细，并累计考试次数。
- 客观题自动判分，主观题需要老师/管理员批改。
- 错题集记录客观题错误答案，学生可标记掌握或删除。

## 文件上传

后端上传目录默认是：

```text
exam-backend/file/
```

该目录属于运行时文件，已加入 `.gitignore`，不建议提交到仓库。


## 开发校验

后端编译：

```bash
cd exam-backend
mvn -q -DskipTests compile
```

前端类型检查：

```bash
cd exam-frontend
npx vue-tsc --ignoreDeprecations 5.0 --noEmit
```

前端构建：

```bash
cd exam-frontend
npm run build
```
