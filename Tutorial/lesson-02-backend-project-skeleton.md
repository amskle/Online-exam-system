# 第 2 课：后端项目骨架与配置体系

> ⏱ 预计时间：90 分钟（概念 25 min + 读代码 40 min + 练习 25 min）
>
> 📁 本课涉及文件：
> `pom.xml` · `application.yml` · 启动类 · 7 个 `config/*` · `Result.java` · `ResultCode.java` · `BusinessException.java` · `GlobalException.java`

---

## 1. 本课目标

学完这节课，你应该能：

- ✅ 解释 `pom.xml` 中每个关键依赖的作用（MyBatis-Plus、JJWT、spring-dotenv 等）
- ✅ 看懂 `application.yml` 中每一段配置控制什么功能
- ✅ 说清 `@Configuration`、`@Bean`、`@Value`、`@ConfigurationProperties` 四者的区别和用法
- ✅ 理解 7 个配置类的职责——CORS、拦截器、分页、密码、文件上传、API 文档、数据库迁移
- ✅ 掌握 `Result<T>` 统一响应 + `BusinessException` 异常 + `GlobalException` 捕获三层协作机制
- ✅ 理解 `@SpringBootApplication` 注解背后的自动配置原理
- ✅ 在项目中新增一个自定义配置类

---

## 2. 概念讲解

### 2.1 为什么这一课要学「骨架」？

如果你把 Spring Boot 项目比作一栋房子：

- **第 1 课的架构** = 城市规划图（房子在哪，和谁连接）
- **第 2 课的骨架** = 房子的承重墙和管线（框架如何组织，配置如何生效）
- **第 3 课之后的业务代码** = 室内的家具和装饰

不把骨架搞清楚就开始堆业务代码，就像不铺水管就开始装修——后面一定漏水。

Spring Boot 的核心哲学是**约定优于配置**（Convention over Configuration）。它默认帮你配好了 90% 的配置，你只需要关注剩下的 10%。本课就是让你理解这 10% 长什么样。

### 2.2 Maven 项目结构与 `pom.xml`

#### 经典 Maven 目录结构

```
exam-backend/
├── pom.xml                          ← 项目对象模型（所有依赖和插件）
├── src/
│   ├── main/
│   │   ├── java/                    ← Java 源码
│   │   │   └── com/example/onlineexamsystem/
│   │   │       ├── OnlineExamSystemApplication.java  ← 启动类
│   │   │       ├── annotation/       ← 自定义注解
│   │   │       ├── common/           ← 公共组件（异常、工具类）
│   │   │       ├── config/           ← 配置类（核心！）
│   │   │       ├── controller/       ← 控制器（处理 HTTP 请求）
│   │   │       ├── interceptor/      ← 拦截器
│   │   │       ├── mapper/           ← 数据访问层
│   │   │       ├── pojo/             ← POJO（entity/dto/vo/api）
│   │   │       ├── service/          ← 服务接口 + 实现
│   │   │       └── utils/            ← 工具类
│   │   └── resources/
│   │       ├── application.yml       ← 核心配置文件
│   │       ├── schema-admin.sql      ← DDL 建表脚本
│   │       └── data-408.sql          ← 种子数据
│   └── test/                         ← 测试代码
```

这个分层不是 Spring Boot 强制的，而是社区约定俗成的**三层架构**变体：

```
Controller（控制层）→ Service（业务层）→ Mapper（数据访问层）
     ↑                    ↑                    ↑
  接收 HTTP 请求      处理业务逻辑         操作数据库
```

#### pom.xml 核心依赖逐一说清

```xml
<!-- 父 POM：锁定 Spring Boot 3.2 全部依赖版本 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<!-- Java 版本 -->
<java.version>21</java.version>
```

`spring-boot-starter-parent` 是 Spring 官方提供的父 POM，它的作用：
1. **版本仲裁**——锁定所有 Spring 相关依赖的版本（你不需要写版本号）
2. **插件配置**——预配置 maven-compiler-plugin 等
3. **资源过滤**——application.yml 中的 `${...}` 占位符自动替换

接下来是核心依赖，按**用途分组**理解：

```xml
<!-- ========== 第 1 组：Web 服务 ========== -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>   <!-- 内嵌 Tomcat + Spring MVC -->
</dependency>
```

`spring-boot-starter-web` 是一个**传递依赖包**，它自动引入：
- `spring-boot-starter`（核心启动器）
- `spring-boot-starter-tomcat`（内嵌 Tomcat 容器）
- `spring-webmvc`（Spring MVC 框架）
- `jackson-databind`（JSON 序列化/反序列化）

> 💡 `starter` 是 Spring Boot 最重要的设计模式——**一个 starter 引入一组相关依赖**，你不需要手动声明十几个依赖。

```xml
<!-- ========== 第 2 组：数据访问 ========== -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.7</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

三个依赖一起构成数据访问层：
- **MyBatis-Plus**：在 MyBatis 基础上增强，封装了 CRUD，支持 Lambda 条件构造器
- **mysql-connector-j**：JDBC 驱动，`scope=runtime` 表示编译时不需要，运行时才加载
- **spring-boot-starter-jdbc**：Spring 的 JDBC 抽象层 + HikariCP 连接池

```xml
<!-- ========== 第 3 组：安全 ========== -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>   <!-- 仅 BCrypt，非完整的 Spring Security -->
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>                <!-- JWT 生成/解析 -->
    <version>0.11.5</version>
</dependency>
```

**重要**：本项目只用 `spring-security-crypto`（BCrypt 加密），不用完整的 Spring Security 框架。这意味着没有 FilterChain、没有 `SecurityContext`——认证由自己写的 `JwtInterceptor` 处理。

> 💡 **面试考点**：为什么不引入完整 Spring Security？因为项目中的鉴权逻辑需要自定义（角色注解 + ThreadLocal + Redis 顶号），完整 Spring Security 的 FilterChain 反而会与之冲突，增加复杂度。

```xml
<!-- ========== 第 4 组：邮件 ========== -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

用于 QQ SMTP 发送注册验证码。

```xml
<!-- ========== 第 5 组：工具 ========== -->
<!-- Lombok：消除样板代码 -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>  <!-- 编译时注解处理器，不打包到最终 jar -->
</dependency>

<!-- spring-dotenv：从 .env 文件加载环境变量 -->
<dependency>
    <groupId>me.paulschwarz</groupId>
    <artifactId>spring-dotenv</artifactId>
    <version>4.0.0</version>
</dependency>

<!-- Swagger/OpenAPI 文档 -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

**`spring-dotenv` 的价值**：把敏感配置（密码、API Key）从 `application.yml` 中分离到 `.env` 文件。`.env` 被 `.gitignore` 忽略，不会提交到 Git。容器部署时通过环境变量注入。

### 2.3 `application.yml` 全景解读

这是项目的**唯一配置文件**，每一个段落控制一个子系统。我们逐段解读。

```yaml
spring:
  application:
    name: Online-exam-system     # 应用名（日志、监控中显示）
```

**数据源配置**（HikariCP 连接池）：

```yaml
  datasource:
    url: jdbc:mysql://localhost:3306/exam?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}           # 从环境变量读取，默认 root
    password: ${DB_PASSWORD:327510}         # 从环境变量读取
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:                                  # HikariCP 连接池参数
      minimum-idle: 5                       # 最小空闲连接数
      maximum-pool-size: 20                 # 最大连接数
      auto-commit: true                     # 自动提交
      idle-timeout: 30000                   # 空闲超时 30s
      pool-name: MyHikariCP                 # 连接池名
      max-lifetime: 1800000                 # 连接最大存活 30min
      connection-timeout: 30000             # 等待连接超时 30s
      connection-test-query: SELECT 1       # 连接存活检测
```

> `${DB_USERNAME:root}` 语法：冒号后面是**默认值**。如果环境变量 `DB_USERNAME` 没设置，就用 `root`。

**Redis 配置**：

```yaml
  data:
    redis:
      host: localhost
      port: 6379
      database: 0                     # 使用 0 号数据库
      lettuce:
        pool:
          max-active: 8               # 最大活跃连接
          max-idle: 8                 # 最大空闲连接
          min-idle: 0
          max-wait: 100ms             # 获取连接最长等待时间
```

**邮件配置**（QQ SMTP）：

```yaml
  mail:
    host: smtp.qq.com
    port: 465                        # SSL 端口
    username: ${MAIL_USERNAME:2061133513@qq.com}
    password: ${MAIL_AUTH_CODE:}     # QQ 邮箱授权码
    properties:
      mail.smtp.ssl.enable: true     # 启用 SSL
      mail.smtp.auth: true           # 启用认证
```

**数据库初始化**：

```yaml
  sql:
    init:
      mode: always                            # 每次启动都执行
      schema-locations: classpath:schema-admin.sql  # DDL 脚本
```

`mode: always` 意味着每次启动都会尝试执行 `schema-admin.sql`。但这个脚本使用了 `CREATE TABLE IF NOT EXISTS`，所以是**幂等的**——重复执行不会出错。

**服务器端口**：

```yaml
server:
  port: 8077
```

**MyBatis-Plus 配置**：

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml   # XML 映射文件路径
  global-config:
    db-config:
      id-type: auto                               # 主键自增
  configuration:
    map-underscore-to-camel-case: true            # 下划线→驼峰自动映射
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # SQL 日志输出到控制台
```

注意 `mapper-locations` 实际指向的路径下**没有 XML 文件**——本项目全部用 MyBatis-Plus 的 `BaseMapper` 接口注解方式，不需要 XML。这个配置是一个保留项。

**自定义配置**：

```yaml
file:
  upload-dir: file           # 上传文件目录（相对于项目根目录）

auth:
  email-code-ttl: 5m         # 邮箱验证码有效期
  email-send-cooldown: 60s   # 发送冷却时间
  email-daily-limit: 10      # 每日发送上限
  trusted-device-ttl: 7d     # 受信设备有效期
  trusted-device-secure-cookie: false

jwt:
  secret: ${JWT_SECRET:t8Kx9mN2vB5qW3pL7sF4cH6jU1yR8eZ0aD5gJ9nM2xP4sV7wC3}
```

这些是项目自定义的配置项。在代码中通过 `@Value("${auth.email-code-ttl}")` 读取。`jwt.secret` 的默认值是给开发环境用的，生产环境必须通过 `JWT_SECRET` 环境变量覆盖。

### 2.4 配置类全景——Spring Boot 如何装配 Bean

Spring Boot 的哲学：**XML 配置 → 注解配置**。所有以 `@Configuration` 标记的类都会被自动扫描并执行。

| 配置类 | 核心注解 | 作用 | 与谁交互 |
|--------|---------|------|---------|
| `CorsConfig` | `@Configuration` | 允许前端跨域请求 | `WebMvcConfigurer` |
| `WebConfig` | `@Configuration` | 注册 JWT 拦截器 | `JwtInterceptor` |
| `MybatisPlusConfig` | `@Configuration` + `@Bean` | 注册分页插件 | MyBatis 拦截器链 |
| `PasswordConfig` | `@Configuration` + `@Bean` | 提供 BCrypt 加密器 | `PasswordEncoder` |
| `FileUploadConfig` | `@Configuration` | 初始化上传目录 + 静态资源映射 | 文件系统 |
| `OpenApiConfig` | `@Configuration` + `@Bean` | 生成 Swagger API 文档 | SpringDoc |
| `DatabaseMigrationRunner` | `@Component` | 启动时增量 DDL + 种子数据 | `JdbcTemplate` |

**关键注解辨析**：

| 注解 | 含义 | 用在 |
|------|------|------|
| `@Configuration` | 标记此类包含 Bean 定义 | 配置类上 |
| `@Bean` | 方法返回值交给 Spring 容器管理 | 方法上 |
| `@Component` | 通用组件，交给 Spring 容器 | 任何类上 |
| `@Value("${key}")` | 从配置文件中读取值 | 字段上 |

> 💡 `@Configuration` 和 `@Component` 的区别：本质上都是让 Spring 管理这个类。但 `@Configuration` 语义更强——它告诉 Spring 这个类里有 `@Bean` 方法，需要特殊代理处理（保证单例）。实践中，专门放 `@Bean` 的类用 `@Configuration`，其他用 `@Component`。

### 2.5 `@SpringBootApplication`——启动类的魔法

```java
@MapperScan("com.example.onlineexamsystem.mapper")
@SpringBootApplication
public class OnlineExamSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlineExamSystemApplication.class, args);
    }
}
```

`@SpringBootApplication` 是三个注解的**组合缩写**：

```java
@SpringBootConfiguration  // = @Configuration（这是一个配置类）
@EnableAutoConfiguration  // 自动配置：根据 classpath 中的 jar 自动装配
@ComponentScan            // 组件扫描：扫描当前包及子包的所有 @Component/@Service 等
```

**`@EnableAutoConfiguration` 如何工作？**

Spring Boot 在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件中列了 100+ 自动配置类。当 classpath 中有 `mysql-connector-j`，`DataSourceAutoConfiguration` 就自动创建数据源；当 classpath 中有 `spring-boot-starter-web`，`WebMvcAutoConfiguration` 就自动配置 MVC。

`@MapperScan` 不是 Spring Boot 原生的，是 MyBatis-Plus 提供的——它扫描 `mapper/` 包下的所有接口，自动生成代理实现类。

### 2.6 统一响应 + 异常处理——三层协作

这是后端 API 设计的**基础设施**，理解它就理解了后端全部接口的返回格式。

**第一层：ResultCode（状态码枚举）**

```java
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    NOT_FOUND(404, "资源未找到"),
    UNAUTHORIZED(401, "无权限"),
    FORBIDDEN(403, "禁止访问"),
    BAD_REQUEST(400, "参数错误");
}
```

**第二层：Result\<T\>（统一响应体）**

每个 API 返回的都是 `Result<T>` 格式。前端拿到的 JSON 永远是：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1719000000000
}
```

设计要点：
- `success()` 重载 5 个版本（无数据 / 有数据 / 自定义消息 / 带分页）
- `fail()` 重载 4 个版本（枚举 / 自定义消息 / 自定义码 / 枚举+消息）
- `@JsonInclude(JsonInclude.Include.NON_NULL)` → 为 null 的字段不出现在 JSON 中

**第三层：BusinessException + GlobalException 捕获**

Controller 代码中遇到错误时，不直接 `return Result.fail()`，而是**抛异常**：

```java
// 在 Service 层
throw new BusinessException("试卷不存在", 404);
```

这个异常被 `@RestControllerAdvice` 全局捕获：

```java
@ExceptionHandler(BusinessException.class)
public Result<Void> handleBusinessException(BusinessException e) {
    return Result.fail(e.getCode(), e.getMessage());
}
```

**为什么这样设计？** 三层协作带来三个好处：

```
Controller 层干净 → 不需要在每个接口写 try-catch
Service 层专注业务 → 遇到错误直接 throw
全局异常处理器兜底 → 统一的错误格式 + 日志
```

异常处理器还覆盖了：
- `ValidationException` → 自定义校验异常
- `MethodArgumentNotValidException` → `@Valid` 参数校验失败
- `Exception` → 未知异常兜底（返回"系统繁忙，请稍后再试"）

---

## 3. 代码阅读（guided walkthrough）

现在打开项目文件，对照着读。

### 3.1 阅读启动类

```
exam-backend/src/main/java/com/example/onlineexamsystem/OnlineExamSystemApplication.java
```

**阅读要点**：

1. `@MapperScan("com.example.onlineexamsystem.mapper")` 放在启动类上——它是一个全局性的配置，告诉 MyBatis-Plus 接口在哪
2. `SpringApplication.run()` 触发了 Spring Boot 的**完整启动流程**：
   - 创建 `ApplicationContext`（Spring 容器）
   - 自动配置（AutoConfiguration）
   - 组件扫描（ComponentScan）
   - 执行 `ApplicationRunner`（如 DatabaseMigrationRunner）
   - 启动内嵌 Tomcat
3. 没有显式 `@ComponentScan` 注解——因为 `@SpringBootApplication` 已包含，默认扫描启动类所在包及子包

**一个常见错误**：如果把启动类放在 `com.example.onlineexamsystem` 包之外，组件扫描范围就变了，很多 Bean 可能不会被自动发现。

### 3.2 阅读 pom.xml

```
exam-backend/pom.xml
```

**对照要点**（不要从头读到尾，而是分组读）：

1. **找到 `spring-boot-starter-web`**——注意到没有写版本号（由父 POM 托管）
2. **找到 `mybatis-plus-spring-boot3-starter`**——注意它写了版本号（非 Spring 官方的依赖自己管版本）
3. **找到 `lombok`**——`<optional>true</optional>` 表示不被传递依赖
4. **找到 `maven-compiler-plugin` 的额外配置**——Lombok 需要 annotationProcessorPaths 才能在编译时生效

### 3.3 阅读 application.yml

```
exam-backend/src/main/resources/application.yml
```

**逐段对照**：

| 段落 | 关键词 | 验证方法 |
|------|--------|---------|
| `spring.datasource` | HikariCP | 启动应用，看日志中的 "HikariPool-1 - Starting..." |
| `spring.data.redis` | lettuce | 启动应用，Redis 连不上会报错 |
| `spring.mail` | QQ SMTP | 配置错误时日志会提示 |
| `spring.sql.init` | `mode: always` | 每次重启都会执行 `schema-admin.sql` |
| `mybatis-plus` | `log-impl` | 执行任何查询，控制台会打印 SQL |
| `jwt.secret` | `${JWT_SECRET:...}` | 用环境变量覆盖测试 |

**关键观察**：`username: ${DB_USERNAME:root}` 和 `password: ${DB_PASSWORD:327510}`——密码从环境变量读。本地开发时如果没有设置 `DB_PASSWORD` 环境变量，就用硬编码的默认值；Docker 部署时，`docker-compose.yml` 注入环境变量覆盖。

### 3.4 阅读 CorsConfig

```
exam-backend/src/main/java/com/example/onlineexamsystem/config/CorsConfig.java
```

- `@Configuration`：标记这是一个配置类
- `implements WebMvcConfigurer`：重写 `addCorsMappings` 方法来自定义 CORS
- `allowedOriginPatterns("*")`：允许任意来源（本地开发方便）
- `allowCredentials(true)`：允许携带 Cookie

> ⚠️ 生产环境应该改为具体的域名列表，而不是 `*`。

### 3.5 阅读 WebConfig

```
exam-backend/src/main/java/com/example/onlineexamsystem/config/WebConfig.java
```

- `@RequiredArgsConstructor`（Lombok）：为 `final` 字段生成构造函数
- `private final JwtInterceptor jwtInterceptor`：Spring 自动注入 `JwtInterceptor`（因为它有 `@Component`）
- `addPathPatterns("/**")`：拦截**所有**请求

**为什么只加拦截器而不加路径排除？** 因为 `JwtInterceptor` 内部自己判断——有 `@Auth` 注解的才检查，没有的直接放行。这样 WebConfig 的逻辑保持简洁。

### 3.6 阅读 MybatisPlusConfig

```
exam-backend/src/main/java/com/example/onlineexamsystem/config/MybatisPlusConfig.java
```

- `@Bean` 方法返回的 `MybatisPlusInterceptor` 被 Spring 管理
- `PaginationInnerInterceptor(DbType.MYSQL)`：MyBatis-Plus 的**物理分页**（不是内存分页）
- 之后在 Service 中只需 `new Page<>(pageNum, pageSize)` 就能自动分页

### 3.7 阅读 PasswordConfig

```
exam-backend/src/main/java/com/example/onlineexamsystem/config/PasswordConfig.java
```

- 把 `BCryptPasswordEncoder` 注册为一个 Bean
- 之后任何地方注入 `PasswordEncoder` 就能用
- BCrypt 的特点：同样的密码每次加密结果不同（自带盐值），不可逆

### 3.8 阅读 FileUploadConfig

```
exam-backend/src/main/java/com/example/onlineexamsystem/config/FileUploadConfig.java
```

- `@Value("${file.upload-dir}")` 从配置文件读取上传目录
- `@PostConstruct`：在 Bean 初始化**之后**自动执行，创建上传目录
- `addResourceHandlers`：把物理路径映射为 URL 访问路径（`/files/**` → 本地目录）

### 3.9 阅读 DatabaseMigrationRunner

```
exam-backend/src/main/java/com/example/onlineexamsystem/config/DatabaseMigrationRunner.java
```

这是本项目最独特的配置类，核心思想是**代码控制的增量数据库迁移**。

- `implements ApplicationRunner`：在所有 Bean 初始化完成后、应用正式对外服务前执行
- `addColumnIfMissing()`：查询 `information_schema.COLUMNS` → 列不存在则执行 ALTER TABLE
- `addIndexIfMissing()`：同理，查询 `information_schema.STATISTICS` → 索引不存在则创建
- `runSeedOnce()`：通过 `data_seed_log` 表防重，确保种子数据只插入一次

**为什么不用 Flyway/Liquibase？** 对于小项目，这两个框架引入了额外的复杂度。自定义 Runner 足够灵活，而且能按需执行复杂的增量逻辑。

### 3.10 阅读 ResultCode + Result + BusinessException + GlobalException

按以下顺序阅读（它们相互引用）：

```
pojo/api/ResultCode.java       ← 1. 先看状态码枚举（6 个值）
pojo/api/Result.java            ← 2. 再看统一响应体（8 个静态工厂方法）
common/exception/BusinessException.java  ← 3. 再看业务异常类
common/exception/GlobalException.java    ← 4. 最后看全局异常处理器
```

**观察 Result.java 的设计模式**：

- 所有构造函数都是 `private`——**强制**通过静态工厂方法创建
- `success()` 有 5 个重载版本——覆盖各种返回场景
- 泛型 `<T>` 让每种返回类型都能用同一个 Result 类
- `@JsonInclude(Include.NON_NULL)` 让 `count` 字段在非分页场景下不出现在 JSON 中

**观察 GlobalException.java 的优先级**：

处理顺序由 `@ExceptionHandler` 决定。Spring 会优先匹配**最具体**的处理器：
```
MethodArgumentNotValidException → ValidationException → BusinessException → Exception
（参数校验）                     （自定义校验）       （业务异常）         （兜底）
```

---

## 4. 动手练习

### 练习 1：新增 Runner 控制台输出（15 min）

**目标**：加深对 `ApplicationRunner` 执行时机的理解，验证大致的启动顺序。

**步骤**：

1. 在 `config/` 包下新建 `StartupLogger.java`：

```java
package com.example.onlineexamsystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupLogger implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("========== 系统启动完成 ==========");
        log.info("服务端口: 8077");
        log.info("Swagger 文档: http://localhost:8077/swagger-ui.html");
        log.info("==================================");
    }
}
```

2. 启动应用，观察控制台输出顺序：
   - Spring Boot 启动日志
   - `DatabaseMigrationRunner` 的 DDL 执行
   - `StartupLogger` 的输出
   - Tomcat 的端口监听

**问题**：`StartupLogger` 和 `DatabaseMigrationRunner` 哪个先执行？如果想让 `StartupLogger` 最后执行，怎么做？（提示：`@Order` 注解）

---

### 练习 2：新增自定义配置项（15 min）

**目标**：掌握 `@ConfigurationProperties` 的使用。

**步骤**：

1. 在 `application.yml` 末尾新增：

```yaml
app:
  feature:
    export-enabled: true
    max-export-rows: 1000
```

2. 在 `config/` 包下新建 `AppFeatureConfig.java`：

```java
package com.example.onlineexamsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.feature")
public class AppFeatureConfig {
    private boolean exportEnabled;
    private int maxExportRows;
}
```

3. 在 `StartupLogger` 中注入并打印这两个值：

```java
private final AppFeatureConfig appFeatureConfig;

// 在 run() 方法中加入：
log.info("导出功能: {}, 最大行数: {}", 
    appFeatureConfig.isExportEnabled(), 
    appFeatureConfig.getMaxExportRows());
```

**验证**：重启应用，确保控制台打印出 `导出功能: true, 最大行数: 1000`。

---

### 练习 3：追踪一个请求的返回格式（15 min）

**目标**：验证 `Result<T>` 在实际响应中的效果。

**步骤**：

1. 启动后端应用
2. 用浏览器或 curl 访问 `http://localhost:8077/swagger-ui.html`（Swagger 文档页）
3. 找到 `/user/login` 接口，发送一个错误的登录请求
4. 观察响应体格式：

```json
{
  "code": 500,
  "message": "账号或密码错误",
  "timestamp": 1719000000000
}
```

5. 再发送一个正确的登录请求（如果有账号），观察返回的不同之处——多了 `data` 字段：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "eyJhbGciOi...",
    "role": 3,
    "roleName": "管理员"
  },
  "timestamp": 1719000000000
}
```

---

### 练习 4：自测题

完成以下问题，检验理解程度：

```
1. Spring Boot 中 @Configuration 和 @Component 有什么区别？
   （提示：语义 + Bean 代理）

2. @SpringBootApplication 由哪三个注解组合而成？
   （提示：不开 IDE 回忆）

3. 为什么 Result<T> 的构造函数都是 private？
   （提示：设计模式）

4. DatabaseMigrationRunner 的 addColumnIfMissing 方法如何判断列是否存在？
   （提示：查询哪张系统表）

5. ${DB_USERNAME:root} 中冒号后面的值是什么意思？
   （提示：如果环境变量不存在...）

6. FileUploadConfig 中 addResourceHandlers 做了什么？
   （提示：URL 路径 → 物理路径映射）

7. 如果 GlobalException 处理了所有 Exception，那业务代码中还能用 try-catch 提前处理吗？
   （提示：异常的冒泡机制）
```

---

## 5. 本课总结

### 核心记忆点

1. **pom.xml** 的核心依赖按用途分 5 组：Web 服务 → 数据访问 → 安全 → 邮件 → 工具
2. **application.yml** 是唯一配置源：数据源、Redis、邮件、MyBatis-Plus、自定义业务配置
3. **7 个配置类**各有其职：CORS、拦截器、分页、密码、文件上传、API 文档、数据库迁移
4. **统一响应三层协作**：
   ```
   Controller 返回 Result<T>
        ↓ 出错时
   Service 抛出 BusinessException
        ↓ 被捕获
   GlobalException 转换为 Result.fail()
   ```
5. **DatabaseMigrationRunner** 是项目特色：用代码实现增量 DDL 迁移 + 种子数据防重

### 下节课预告

第 3 课我们将深入数据库设计——8 张表的 ER 关系图、MyBatis-Plus 的实际用法、实体类与数据库表的映射规则。你会理解多对多关系如何拆解、错题集的设计意图、以及为什么 MyBatis-Plus 的 `BaseMapper` 让数据访问层如此简洁。

---

> 📌 本课属于「在线考试系统全栈课程」，完整课表见 `CURRICULUM.md`。
