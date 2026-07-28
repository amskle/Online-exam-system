# 第 5 课：认证鉴权系统（JWT + 拦截器）

> ⏱ 预计时间：100 分钟（概念 35 min + 读代码 40 min + 练习 25 min）
>
> 📁 本课涉及文件：
> `Auth.java` · `JwtInterceptor.java` · `JwtUtil.java` · `UserContext.java` · `RedisUtil.java` · `WebConfig.java` · `BaseUserServiceImpl.java`（login 方法）· `BaseUserController.java`（login/logout 端点）· `RoleEnum.java` · 前端 `request.ts` · 前端 `localStorage.ts`

---

## 1. 本课目标

学完这节课，你应该能：

- ✅ 画出 JWT 的三段式结构（Header + Payload + Signature），说清为什么用 JWT 而不是 Session
- ✅ 理解 `@Auth` 自定义注解的定义方式——元注解 `@Target` / `@Retention` 各是什么意思
- ✅ 完整追踪 `JwtInterceptor.preHandle()` 的执行流程——从 Token 提取到角色校验
- ✅ 理解 `UserContext`（ThreadLocal）的线程隔离原理——为什么每个请求能拿到"当前用户"
- ✅ 说清顶号检测的完整链路：登录时写 Redis → 请求时比对 → 不一致时踢下线
- ✅ 理解前端 Token 管理：sessionStorage vs localStorage、请求拦截器、401 响应处理

---

## 2. 概念讲解

### 2.1 为什么用 JWT 而不是 Session？

在传统的 Session 模式中：

```
用户登录 → 服务器创建 Session → 把 Session ID 存到 Cookie
        → 后续请求：浏览器自动带 Cookie → 服务器查 Session 表 → 取出用户信息
```

**Session 的问题**：

| 问题 | 说明 |
|------|------|
| **服务器有状态** | Session 存在服务器内存/Redis 中，水平扩展时需要共享 Session |
| **Cookie 跨域限制** | 前后端分离部署时（不同域名），Cookie 默认不跨域发送 |
| **移动端不友好** | 原生 App 没有 Cookie 概念，需要手动管理 |

**JWT（JSON Web Token）解决方案**：

```
用户登录 → 服务器生成 JWT → 返回给客户端
        → 后续请求：客户端在 Authorization 头中带上 "Bearer <token>"
        → 服务器用密钥验签 → 直接从 Token 中读出用户信息（无需查数据库）
```

JWT 的核心优势是**无状态**——Token 本身包含了用户身份信息，服务器不需要维护 Session。这对微服务架构特别友好：任何服务拿到同一个 JWT，用同一个密钥就能验证。

> 💡 **面试考点**：JWT 不是加密的，是**签名的**。Payload 部分是 Base64 编码（任何人都能解码看到内容），但 Signature 保证了内容没被篡改。所以**绝不要把密码、身份证号等敏感信息放进 JWT Payload**。

### 2.2 JWT 三段式结构

一个真实的 JWT Token 长这样：

```
eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoxLCJzdWIiOiI1IiwiaWF0IjoxNzE5MDAwMDAwLCJleHAiOjE3MTk2MDQ4MDB9.abc123def456
```

用 `.` 分隔成三段：

```
Header.Payload.Signature
```

#### 第一段：Header（算法声明）

```json
{
  "alg": "HS256",   // HMAC-SHA256 签名算法
  "typ": "JWT"
}
```

Base64 编码后 → `eyJhbGciOiJIUzI1NiJ9`

#### 第二段：Payload（载荷 —— 自定义数据）

```json
{
  "role": 1,                              // 用户角色（本项目的自定义 claim）
  "sub": "5",                             // subject = 用户 ID（JWT 标准字段）
  "iat": 1719000000,                      // issued at = 签发时间（JWT 标准字段）
  "exp": 1719604800,                      // expiration = 过期时间（JWT 标准字段）
  "loginVer": "a1b2c3d4-e5f6-..."         // 登录版本号（本项目的自定义 claim，用于顶号）
}
```

Base64 编码后 → `eyJyb2xlIjoxLCJzdWIiOiI1IiwiaWF0I...`

#### 第三段：Signature（签名 —— 防篡改）

```
HMAC-SHA256(
    base64Url(header) + "." + base64Url(payload),
    secretKey   ← 只有服务器知道的密钥
)
```

**签名的意义**：如果攻击者修改了 Payload（比如把 `"role": 1` 改成 `"role": 3`），Signature 就对不上了，服务器验签时会直接拒绝。

#### 对应到本项目代码

`JwtUtil.java` 中生成了 JWT 的完整过程：

```java
// JwtUtil.java:51-64
public String generateToken(Integer userId, Integer role, String loginVersion) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", role);                     // 自定义 claim：角色
    if (loginVersion != null) {
        claims.put("loginVer", loginVersion);      // 自定义 claim：登录版本号
    }
    return Jwts.builder()
            .setClaims(claims)                     // Payload 部分
            .setSubject(String.valueOf(userId))    // sub 字段（用户 ID）
            .setIssuedAt(new Date())               // iat 字段
            .setExpiration(new Date(               // exp 字段（7天后过期）
                System.currentTimeMillis() + EXPIRATION_TIME))
            .signWith(getKey(), SignatureAlgorithm.HS256)  // 签名
            .compact();                            // 拼接成三段式字符串
}
```

**Token 过期时间是 7 天**：

```java
private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000;  // 7天
```

> 💡 **为什么是 7 天？** 太短了用户体验差（频繁重新登录），太长了安全风险大（Token 泄露后被长期利用）。7 天是一个折中方案，同时配合"顶号检测"机制——即使 Token 没过期，也能强制让它失效。

### 2.3 `@Auth` 注解 —— 声明式权限控制

#### 自定义注解的定义

```java
// Auth.java
@Target({ElementType.METHOD, ElementType.TYPE})   // ① 可以加在方法上和类上
@Retention(RetentionPolicy.RUNTIME)                // ② 运行时保留（反射能读到）
@Documented                                        // ③ 生成 Javadoc 时包含
public @interface Auth {
    int[] value() default {};  // ④ 允许的角色数组，为空表示"只需要登录"
}
```

**四个元注解的含义**：

| 元注解 | 值 | 含义 |
|--------|-----|------|
| `@Target` | `{METHOD, TYPE}` | 这个注解可以加在**方法**上和**类**上 |
| `@Retention` | `RUNTIME` | 注解信息在**运行时**仍然保留——这样反射才能读到它 |
| `@Documented` | — | 生成 Javadoc 文档时包含此注解 |

**关于 `@Retention` 的三种级别**：

```
SOURCE   → 只在源码中存在，编译时丢弃（如 @Override）
CLASS    → 编译到 .class 文件中，但运行时 JVM 不加载（默认值）
RUNTIME  → 运行时可通过反射读取（自定义注解几乎都用这个）
```

如果 `@Auth` 用了 `RetentionPolicy.CLASS`，`JwtInterceptor` 中的反射代码就读取不到注解信息，鉴权就完全失效——这是一个非常隐蔽的 bug。

#### 三种使用方式

```java
// 方式 1：类级别 —— 整个 Controller 都需要相同权限
@RestController
@RequestMapping("/question")
@Auth({2, 3})              // 所有方法都需要教师或管理员
public class QuestionController { ... }

// 方式 2：方法级别 —— 覆盖类级别（或单独声明）
@GetMapping("/listPage")
@Auth(3)                   // 只有管理员能看（即使类上是 @Auth({2,3})）
public Result<PageVO<Question>> listPage(...) { ... }

// 方式 3：不需要指定角色 —— 只要登录了就能访问
@Auth                       // value = {}（空数组），只校验登录状态
public Result<...> someMethod(...) { ... }
```

**方法优先于类**：`getAuthAnnotation()` 的逻辑是先查方法上的注解，找不到再查类上的。这意味着你可以在类上放一个宽松的 `@Auth({1,2,3})`，然后在敏感方法上覆盖更严格的 `@Auth(3)`。

#### 角色值的设计

角色值和数据库中的 `role` 字段一一对应，没有额外的中间层：

```java
// RoleEnum.java
STUDENT(1, "学生"),
TEACHER(2, "教师"),
ADMIN(3, "管理员");
```

```java
// @Auth(1)      → 只有学生
// @Auth(3)      → 只有管理员
// @Auth({1,2})  → 学生或教师
```

这种设计让"角色常量" = "数据库值" = "前端枚举值"，三端统一，不会出现映射错误。

> 💡 **为什么 int[] 而不是 enum？** 注解中的属性类型有限制——只能是基本类型、String、Class、枚举、注解，以及它们的数组。但用 `int[]` 比 `RoleEnum[]` 更灵活——如果将来新增角色，不需要修改注解定义。

### 2.4 `JwtInterceptor` —— 拦截器全流程

这是本课最重要的代码。拦截器是 Spring MVC 的"AOP 横切面"——在所有请求到达 Controller **之前**执行。

#### 注册拦截器

```java
// WebConfig.java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**");   // 拦截所有路径
    }
}
```

拦截所有 `/api/**` 路径（因为 `application.yml` 中 `server.servlet.context-path=/api`，所以这里的 `/**` 实际是 `/api/**`）。

#### 完整执行流程图

```
请求到达 → JwtInterceptor.preHandle()
    │
    ├─ ① 检查白名单
    │   path 是否以 /user/login、/user/register、/files/upload 开头？
    │   ├─ 是 → return true（直接放行，不检查 Token）
    │   └─ 否 → 继续
    │
    ├─ ② 检查是否是 HandlerMethod
    │   handler instanceof HandlerMethod？
    │   ├─ 否（如静态资源请求）→ return true
    │   └─ 是 → 继续
    │
    ├─ ③ 获取 @Auth 注解
    │   先查方法 → 再查类 → 都没有？
    │   ├─ 没有 @Auth → return true（公开接口）
    │   └─ 有 @Auth → 继续
    │
    ├─ ④ 提取 Token
    │   从 Authorization Header 提取 → Bearer 前缀处理
    │   Header 没有？→ 回退到 Cookie (exam_token)
    │   都没有？→ 返回 401 "未提供认证令牌"
    │
    ├─ ⑤ 解析 Token
    │   jwtUtil.getClaims(token) → 验签 + 解析
    │   失败？→ 返回 401 "认证失败"
    │
    ├─ ⑥ 设置 ThreadLocal
    │   UserContext.setUser(userId, role)
    │
    ├─ ⑦ 顶号检测
    │   Token 中有 loginVer？
    │   Redis 中有 user:login_version:<userId>？
    │   二者不一致？→ 返回 401 "账号已在其他设备登录"
    │   Redis 不可用（storedVersion == null）？→ 放行（避免误伤）
    │
    ├─ ⑧ 角色校验
    │   @Auth 的 value 为空？→ return true（只需要登录）
    │   当前用户 role 在允许列表中？→ return true
    │   不在？→ 返回 403 "无权限访问"
    │
    └─ ⑨ 全部通过 → return true → 请求到达 Controller
```

#### 源码逐段解读

**① 白名单机制**：

```java
private static final String[] EXCLUDE_PATHS = {
        "/user/login",
        "/user/register",
        "/files/upload"
};

private boolean isExcludePath(String path) {
    for (String excludePath : EXCLUDE_PATHS) {
        if (path.startsWith(excludePath)) {
            return true;
        }
    }
    return false;
}
```

为什么这三条路径是白名单？因为登录和注册时还没有 Token，文件上传可能被第三方工具调用。

> ⚠️ **注意**：`startsWith` 匹配不是最精确的方式——`/user/loginBackdoor` 也会被放行。但对于内部项目来说，这已足够。更严谨的做法是用 AntPathMatcher 做精确匹配。

**② 为什么检查 `handler instanceof HandlerMethod`？** Spring 不仅处理 Controller 请求——还会处理静态资源、错误页等。这些非 Controller 请求的 handler 不是 `HandlerMethod`，不需要鉴权。

**③ 获取注解 —— 方法优先于类**：

```java
private Auth getAuthAnnotation(Method method) {
    if (method.isAnnotationPresent(Auth.class)) {
        return method.getAnnotation(Auth.class);   // 方法级别优先
    }
    Class<?> declaringClass = method.getDeclaringClass();
    if (declaringClass.isAnnotationPresent(Auth.class)) {
        return declaringClass.getAnnotation(Auth.class);  // 回退到类级别
    }
    return null;  // 没有 @Auth → 公开接口
}
```

**④ Token 提取 —— Header 优先 + Cookie 回退**：

```java
String token = request.getHeader("Authorization");
if (token == null || token.trim().isEmpty()) {
    token = extractTokenFromCookie(request);  // 回退到 Cookie
}

if (token.startsWith("Bearer ")) {
    token = token.substring(7);  // 去掉 "Bearer " 前缀
}
```

两种 Token 传递方式的对比：

| 方式 | 前端代码 | 优点 | 缺点 |
|------|---------|------|------|
| **Authorization Header** | `config.headers.Authorization = 'Bearer ' + token` | 不受 Cookie 跨域限制 | 需要手动写代码添加 |
| **HttpOnly Cookie** | 服务器 `Set-Cookie` 时设置 `HttpOnly` | JS 无法读取，防 XSS | 受 Cookie 跨域和 SameSite 限制 |

本项目同时支持两种方式——前端正常请求走 Header，某些特殊场景（如从邮件链接点击过来）走 Cookie。

**⑤ Token 解析 —— 一个调用完成验签 + 提取**：

```java
Claims claims = jwtUtil.getClaims(token);
Integer userId = jwtUtil.getUserId(token);
Integer role = jwtUtil.getRole(token);
```

`getClaims()` 内部调用了 JJWT 库的 `parseClaimsJws()`——这一步同时做两件事：**验证签名** + **解析 Payload**。如果签名不对或 Token 过期，直接抛异常。

**⑥ 设置 ThreadLocal**：

```java
UserContext.setUser(userId, role);
```

这一步之后，整个请求生命周期内的任何代码都可以通过 `UserContext.getUserId()` 获取当前用户——无需在方法间层层传递 userId 参数。详见 2.5 节。

**⑦ 顶号检测**：

```java
String loginVersion = jwtUtil.getLoginVersion(token);
if (loginVersion != null) {
    String storedVersion = redisUtil.get("user:login_version:" + userId);
    if (storedVersion != null && !storedVersion.equals(loginVersion)) {
        handleUnauthorized(response, "账号已在其他设备登录，请重新登录");
        return false;
    }
}
```

详见 2.6 节。

**⑧ 角色校验**：

```java
int[] requiredRoles = auth.value();
if (requiredRoles.length > 0) {
    Integer currentRole = UserContext.getRole();
    if (currentRole == null || !hasRequiredRole(currentRole, requiredRoles)) {
        handleForbidden(response, "无权限访问");
        return false;
    }
}
```

`@Auth(3)` → `requiredRoles = [3]` → 只有当 `currentRole == 3` 时才放行。`@Auth({1,2})` → `requiredRoles = [1,2]` → 只要 `currentRole` 是 1 或 2 就放行。

**⑨ 错误响应的写法**：

```java
private void handleUnauthorized(HttpServletResponse response, String message) throws Exception {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // HTTP 401
    response.setContentType("application/json; charset=utf-8");
    Result<String> fail = Result.fail(ResultCode.UNAUTHORIZED, message);
    response.getWriter().write(new ObjectMapper().writeValueAsString(fail));
}
```

注意这里是**直接写 `HttpServletResponse`**，而不是抛异常。因为这是在拦截器中——Spring 的 `@ExceptionHandler`（`GlobalException`）在拦截器抛出异常时处理机制不同。直接写 response 是最可靠的做法。

返回的 JSON 格式与正常 Controller 完全一致：
```json
{"code": 401, "message": "账号已在其他设备登录，请重新登录", "timestamp": 1719000000000}
```

### 2.5 `UserContext` —— ThreadLocal 线程隔离

```java
// UserContext.java
public class UserContext {
    public record UserInfo(Integer userId, Integer role) {}

    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();

    public static void setUser(Integer userId, Integer role) {
        CONTEXT.set(new UserInfo(userId, role));
    }

    public static Integer getUserId() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.userId : null;
    }

    public static Integer getRole() {
        UserInfo userInfo = CONTEXT.get();
        return userInfo != null ? userInfo.role : null;
    }

    public static void clear() {
        CONTEXT.remove();  // ⚠️ 必须清理，防止内存泄漏！
    }
}
```

#### ThreadLocal 的原理

```
Tomcat 线程池：每个请求分配一个线程

请求 A（线程-1）                   请求 B（线程-2）
    │                                   │
    ├─ JwtInterceptor                   ├─ JwtInterceptor
    │   UserContext.setUser(5, 1)       │   UserContext.setUser(8, 2)
    │   → 写入 线程-1 的 ThreadLocal    │   → 写入 线程-2 的 ThreadLocal
    │                                   │
    ├─ Controller                       ├─ Controller
    │   UserContext.getUserId() → 5     │   UserContext.getUserId() → 8
    │   （从线程-1 的 ThreadLocal 读）  │   （从线程-2 的 ThreadLocal 读）
    │                                   │
    └─ 请求结束（线程归还线程池）       └─ 请求结束（线程归还线程池）
       UserContext.clear() ← 清理！        UserContext.clear() ← 清理！
```

**关键理解**：每个线程的 ThreadLocal 是独立的。线程-1 里 set 的值，线程-2 永远读不到。这就是"线程隔离"——天然线程安全，不需要加锁。

#### 在业务代码中使用

```java
// StudentExamController.java 中随处可见：
@PostMapping("/examRecords/start")
public Result<ExamRecord> start(@RequestParam Integer paperId) {
    Integer userId = UserContext.getUserId();  // 直接获取，无需从参数传入
    // ...
}
```

**为什么不在 Controller 参数中传 userId？** 因为前端不可信——攻击者可以伪造任意 userId。从 JWT 中解析出的 userId 是经过签名验证的，无法伪造。

#### 为什么必须 `clear()`？

Tomcat 使用线程池——线程在处理完一个请求后**不会销毁**，而是归还到池中等待下一个请求。如果不清理 ThreadLocal：

```
线程-1 处理请求 A（userId=5）→ 归还线程池（ThreadLocal 中残留 userId=5）
      ↓
线程-1 处理请求 B（未登录，没有 @Auth）→ UserContext.getUserId() 竟然返回 5！
      ↓
这是一个严重的安全漏洞——未登录用户可以"借用"上一个用户的身份
```

所以 `clear()` 不是可选的——是**必须的**。本项目没有在拦截器的 `afterCompletion` 中清理，依赖了 Tomcat 线程池的 ThreadLocal 清理机制。最佳实践是在 `afterCompletion` 中显式调用 `UserContext.clear()`。

> 💡 **面试考点**：ThreadLocal 内存泄漏是怎么发生的？Thread → ThreadLocalMap → Entry（弱引用 key + 强引用 value）。ThreadLocal 对象被 GC 后，key 变成 null，但 value 永远不会被回收（因为 Thread → ThreadLocalMap → Entry → value 是强引用链）。`remove()` 方法清除整个 Entry，避免了这种泄漏。

### 2.6 顶号检测 —— 完整链路

这是本项目最精妙的设计之一。"顶号"就是——当你在设备 B 上登录了同一个账号，设备 A 上的登录应该被"踢下线"。

#### 完整时序图

```
时间线 →

设备 A                          服务器                         Redis
  │                               │                              │
  ├─ 登录(account=admin) ────────►│                              │
  │                               ├─ 生成 loginVersion="v1"      │
  │                               ├─ SET user:login_version:1    │
  │                               │   = "v1" (TTL 7天) ─────────►│
  │                               ├─ 签发 JWT 包含 loginVer:"v1" │
  │  ◄── 返回 JWT(v1) ───────────┤                              │
  │                               │                              │
  ├─ 请求 GET /question/list ────►│                              │
  │   Authorization: Bearer JWT   ├─ 解析 JWT → loginVer="v1"   │
  │                               ├─ GET user:login_version:1 ──►│
  │                               │  ◄── 返回 "v1"               │
  │                               ├─ "v1" == "v1" ✅ 通过        │
  │  ◄── 200 OK ─────────────────┤                              │
  │                               │                              │
  │               设备 B                                         │
  │                 │                                             │
  │                 ├─ 登录(account=admin) ──────►                │
  │                 │                             ├─ 生成 loginVersion="v2"
  │                 │                             ├─ SET user:login_version:1
  │                 │                             │   = "v2" ────►│ 覆盖了！
  │                 │                             ├─ 签发 JWT 包含 loginVer:"v2"
  │                 │  ◄── 返回 JWT(v2) ─────────┤               │
  │                 │                             │               │
  ├─ 请求 GET /question/list ──────────────────────┤               │
  │   Authorization: Bearer JWT(v1)               │               │
  │                                               ├─ 解析 JWT → loginVer="v1"
  │                                               ├─ GET user:login_version:1 ──►│
  │                                               │  ◄── 返回 "v2"               │
  │                                               ├─ "v1" != "v2" ❌ 被顶号！
  │  ◄── 401 "账号已在其他设备登录" ──────────────┤
```

#### 对应代码

**登录时写入版本号**（`BaseUserServiceImpl.login()`）：

```java
String loginVersion = UUID.randomUUID().toString();
redisUtil.put("user:login_version:" + baseUser.getId(), loginVersion, Duration.ofDays(7));
String token = jwtUtil.generateToken(baseUser.getId(), baseUser.getRole(), loginVersion);
```

**每次请求时比对**（`JwtInterceptor.preHandle()`）：

```java
String loginVersion = jwtUtil.getLoginVersion(token);
if (loginVersion != null) {
    String storedVersion = redisUtil.get("user:login_version:" + userId);
    if (storedVersion != null && !storedVersion.equals(loginVersion)) {
        handleUnauthorized(response, "账号已在其他设备登录，请重新登录");
        return false;
    }
}
```

#### 关键设计决策

**为什么 storedVersion == null 时放行？** Redis 可能挂了，或者 key 过期了。如果 Redis 不可用时拒绝所有请求，那就是"宁可错杀一千"——整个系统都不可用。放行是更务实的做法。

**为什么 Redis key 的 TTL 和 JWT 过期时间一致（7天）？** JWT 本身过期后，即使 Redis 中还有 loginVersion，拦截器也无法通过 JWT 解析那一步（JWT 过期 → `getClaims()` 抛异常）。所以 Redis key 不需要比 JWT 活得更久。

**为什么用 UUID 而不是时间戳？** 时间戳可能在同一毫秒内重复（如果同一用户在不同设备上几乎同时登录）。UUID 是全局唯一的，不会有碰撞。

### 2.7 前端 Token 管理

#### 存储策略：sessionStorage vs localStorage

```typescript
// localStorage.ts
const sessionStore = {
  get(key: string) { return sessionStorage.getItem(key) },
  set(key: string, value: string) { sessionStorage.setItem(key, value) },
  remove(key: string) { sessionStorage.removeItem(key) }
}

export const setToken = (token: string) => {
  sessionStore.set(TOKEN_KEY, token)   // ← 存在 sessionStorage！
}
```

| 存储方式 | 生命周期 | 本项目用途 |
|----------|---------|-----------|
| **sessionStorage** | 关闭标签页即清除 | Token、Role、UserId（主存储） |
| **localStorage** | 永久（直到手动清除） | 仅"记住账号"功能 |

**为什么 Token 存在 sessionStorage？**

1. **多 Tab 隔离**：同一个浏览器的两个 Tab 可以用不同账号登录（比如管理员在 Tab1，学生在 Tab2），互不干扰
2. **自动清理**：关闭标签页 Token 自动消失——比手动设置过期时间更简单可靠
3. **防 XSS**：虽然不如 HttpOnly Cookie 安全，但至少 Token 不会在浏览器重启后仍然存在

**"记住我"功能**：只有账号名存在 localStorage（非敏感信息），Token 仍然在 sessionStorage 中。这意味着用户下次打开浏览器时，只需要输入密码，不需要重新输入账号。

#### Axios 拦截器 —— Token 自动注入

```typescript
// request.ts —— 请求拦截器
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
        config.headers.Authorization = `Bearer ${token}`  // 自动注入
    }
    return config
})
```

前端开发者不需要在每个 API 调用中手动传 Token——拦截器自动从 sessionStorage 读取并注入到 Header 中。

#### 401 响应处理 —— 自动跳转登录

```typescript
// request.ts —— 响应拦截器
instance.interceptors.response.use(
    (response: AxiosResponse) => {
        if (response.data.code !== 200) {
            return Promise.reject(response.data)  // 业务错误 → reject
        }
        return response.data
    },
    (error) => {
        if (error.response?.status === 401) {     // HTTP 401（未登录/被顶号）
            const msg = error.response?.data?.message || '登录已过期，请重新登录'
            clearAllAuth()                         // 清除本地 Token
            if (window.location.pathname !== '/login') {
                ElMessage.warning(msg)             // 提示用户
                window.location.href = '/login'    // 跳转登录页
            }
            return Promise.reject(error)
        }
        ElMessage.error(error.message || '请求失败，请稍后再试')
        return Promise.reject(error)
    }
)
```

**被顶号的用户体验**：用户在设备 A 上操作 → 设备 B 登录同一账号 → 设备 A 发请求 → 返回 401 "账号已在其他设备登录" → 弹窗提示 + 清空 Token + 跳转登录页。

---

## 3. 代码阅读（guided walkthrough）

### 3.1 `JwtUtil.java` 全览

```
utils/JwtUtil.java（124 行）
```

**整体结构**：

```
JwtUtil
├─ secretKey                    ← 从 application.yml 注入（${jwt.secret}）
├─ EXPIRATION_TIME              ← 常量：7天
├─ CLAIM_LOGIN_VERSION          ← 常量："loginVer"
│
├─ getKey()                     ← 将 secret 转为 HMAC 密钥对象
├─ generateToken(userId, role)  ← 生成 JWT（不带登录版本号，兼容旧调用）
├─ generateToken(userId, role, loginVersion) ← 生成 JWT（带登录版本号）
│
├─ getClaims(token)             ← 解析 + 验签 → 返回 Claims
├─ getUserId(token)             ← 从 Claims 中提取 userId
├─ getRole(token)               ← 从 Claims 中提取 role
├─ getLoginVersion(token)       ← 从 Claims 中提取 loginVer
├─ validateToken(token)         ← 验签是否通过（不提取数据）
```

**`getKey()` 方法 —— 密钥转换**：

```java
private Key getKey() {
    return Keys.hmacShaKeyFor(secretKey.getBytes());
}
```

`secretKey` 来自 `application.yml` 中的 `jwt.secret` 配置项。在实际部署中，这个值通过环境变量注入，不会写在配置文件里。

> ⚠️ **安全警告**：JWT 的安全性完全依赖 `secretKey` 的保密性。任何人拿到这个密钥都能伪造任意用户的 Token。生产环境中，密钥应该足够长（至少 256 位）、随机生成、通过环境变量或密钥管理服务注入。

### 3.2 `WebConfig.java` —— 拦截器的注册方式

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**");  // 拦截所有路径
    }
}
```

这是 Spring MVC 配置拦截器的标准方式。`addPathPatterns("/**")` 表示所有请求都经过这个拦截器（实际路径是 `/api/**`，因为 context-path 是 `/api`）。

如果需要排除某些路径，可以用 `.excludePathPatterns("/health", "/public/**")`。但本项目选择在拦截器内部用 `isExcludePath()` 做白名单判断——这样白名单逻辑集中在拦截器代码中，不分散在配置里。

### 3.3 端到端链路追踪

选择一个具体的场景——学生查看自己的考试记录——追踪整个认证链路：

```
前端（Vue 页面加载）
  │
  ├─ 1. 从 sessionStorage 读取 Token
  │     const token = getToken()
  │
  ├─ 2. Axios 请求拦截器注入 Header
  │     config.headers.Authorization = `Bearer ${token}`
  │
  ├─ 3. 发起 HTTP 请求
  │     GET /api/student/examRecords/listPage?pageNum=1&pageSize=10
  │     Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoxLCJ...
  │
  ▼
后端（Spring Boot）
  │
  ├─ 4. JwtInterceptor.preHandle()
  │   ├─ path = "/student/examRecords/listPage"
  │   ├─ 不在白名单 → 继续
  │   ├─ 是 HandlerMethod → 继续
  │   ├─ StudentExamController 类上有 @Auth(1) → 需要鉴权
  │   ├─ 提取 Token → "Bearer eyJ..." → "eyJ..."
  │   ├─ 解析 → userId=5, role=1, loginVer="v1"
  │   ├─ UserContext.setUser(5, 1)
  │   ├─ 顶号检测 → Redis 中 loginVer = "v1" ✅
  │   ├─ @Auth(1) requiredRoles=[1], currentRole=1 ✅
  │   └─ return true → 放行
  │
  ├─ 5. StudentExamController.listPage()
  │   ├─ Integer userId = UserContext.getUserId()  → 5
  │   ├─ 查询该学生的考试记录
  │   └─ return Result.success(pageVO)
  │
  ▼
前端
  │
  ├─ 6. 响应拦截器
  │   ├─ response.data.code === 200? → 是 → return response.data
  │   └─ 页面渲染考试记录列表
```

如果是被顶号的场景，步骤 4 会变成：

```
├─ 4'. 顶号检测 → Redis 中 loginVer = "v2" ≠ JWT 中的 "v1"
│   ├─ handleUnauthorized(response, "账号已在其他设备登录，请重新登录")
│   └─ return false  ← 请求被拦截，不会到达 Controller
│
▼
前端收到 HTTP 401
  ├─ 响应拦截器 → clearAllAuth() → 弹窗 → 跳转 /login
```

---

## 4. 动手练习

### 练习 1：追踪拦截器执行（15 min）

**目标**：在 `JwtInterceptor.preHandle()` 中添加日志，观察每次请求的拦截过程。

**步骤 1**：在 `JwtInterceptor` 中引入 Slf4j：

```java
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j                                  // ← 添加这个注解
public class JwtInterceptor implements HandlerInterceptor {
```

**步骤 2**：在 `preHandle()` 方法的关键节点添加日志：

```java
@Override
public boolean preHandle(HttpServletRequest request,
                         HttpServletResponse response,
                         Object handler) throws Exception {
    String path = request.getRequestURI();
    log.info("🔵 拦截请求：{}", path);    // ← 入口日志

    // 白名单检查后
    if (isExcludePath(path)) {
        log.info("⬜ 白名单放行：{}", path);  // ← 白名单日志
        return true;
    }
    // ... 后续代码 ...

    // 设置 ThreadLocal 后
    UserContext.setUser(userId, role);
    log.info("👤 用户上下文已设置：userId={}, role={}", userId, role);  // ← 用户信息日志

    // 放行前
    log.info("✅ 鉴权通过：{} (userId={}, role={})", path, userId, role);  // ← 通过日志
    return true;
}
```

**验证**：重启后端，打开浏览器访问不同页面，观察控制台日志输出：
```
🔵 拦截请求：/student/examPapers/listPage
👤 用户上下文已设置：userId=5, role=1
✅ 鉴权通过：/student/examPapers/listPage (userId=5, role=1)

🔵 拦截请求：/user/login
⬜ 白名单放行：/user/login
```

---

### 练习 2：实现登录失败次数限制（20 min）

**目标**：使用 Redis 实现"5 次登录失败后锁定 15 分钟"，加深对 Redis 和异常处理的理解。

**步骤 1**：在 `RedisUtil.java` 中添加一个辅助方法：

```java
/**
 * 记录登录失败次数（返回当前失败次数）
 *
 * @param key        Redis key（如 "login_fail:admin"）
 * @param ttl        过期时间（如 15 分钟）
 * @param maxAttempts 最大失败次数（超过后不再递增）
 * @return 当前失败次数
 */
public long recordLoginFailure(String key, Duration ttl, int maxAttempts) {
    long count = increment(key, ttl);
    if (count > maxAttempts) {
        return maxAttempts;  // 超过最大值时返回最大值（已被锁定）
    }
    return count;
}
```

**步骤 2**：在 `BaseUserServiceImpl.login()` 中添加失败次数检查：

```java
public UserLoginResponseVO login(UserLoginDTO userLoginDTO) {
    String failKey = "login_fail:" + userLoginDTO.getAccount();

    // ① 检查是否已被锁定
    String failCountStr = redisUtil.get(failKey);
    if (failCountStr != null && Integer.parseInt(failCountStr) >= 5) {
        long remainingSeconds = redisUtil.getExpireSeconds(failKey);
        throw new BusinessException(
            "账号已被锁定，请 " + (remainingSeconds / 60 + 1) + " 分钟后重试");
    }

    // ② 查询用户（原有逻辑）
    BaseUser baseUser = this.getOne(
        new LambdaQueryWrapper<BaseUser>()
            .eq(BaseUser::getAccount, userLoginDTO.getAccount())
    );
    if (baseUser == null) {
        recordFailure(failKey);  // 账号不存在也算一次失败
        throw new BusinessException("账号不存在");
    }

    // ③ 密码判断
    boolean passwordMatched = baseUser.getPassword().startsWith("$2")
            ? passwordEncoder.matches(userLoginDTO.getPassword(), baseUser.getPassword())
            : Objects.equals(baseUser.getPassword(), userLoginDTO.getPassword());
    if (!passwordMatched) {
        recordFailure(failKey);
        throw new BusinessException("密码错误");
    }

    // ④ 登录成功 → 清除失败计数
    redisUtil.delete(failKey);

    // ... 后续生成 Token 的逻辑不变 ...
}

private void recordFailure(String failKey) {
    long count = redisUtil.recordLoginFailure(failKey, Duration.ofMinutes(15), 5);
    if (count >= 5) {
        throw new BusinessException("密码错误次数过多，账号已被锁定15分钟");
    }
}
```

**验证**：用错误的密码连续登录 5 次，第 6 次应该被拒绝（即使密码正确了也会被拒绝），15 分钟后自动恢复。

---

### 练习 3：自测题

```
1. JWT 的三段结构中，哪一段是用来防止篡改的？它是如何工作的？
   （提示：Signature 的生成公式是什么？为什么攻击者改了 Payload 会失败？）

2. @Auth 注解的 @Retention(RUNTIME) 如果误写成 @Retention(SOURCE)，会发生什么？
   （提示：JwtInterceptor 中用什么方式读取 @Auth 注解？）

3. ThreadLocal 为什么是线程安全的？如果忘了调用 clear() 会导致什么问题？
   （提示：Tomcat 线程池机制）

4. 顶号检测中，如果 Redis 不可用（storedVersion == null），本项目选择"放行"——
   为什么？这是否会带来安全风险？
   （提示：可用性 vs 安全性的权衡）

5. 前端为什么把 Token 存在 sessionStorage 而不是 localStorage？
   说出至少两个理由。
   （提示：多 Tab 场景、自动清理）

6. JwtInterceptor 中既有"白名单"检查，也有"没有 @Auth 注解就放行"的逻辑——
   两者有什么区别？为什么需要同时存在？
   （提示：登录接口 vs 公开查询接口）

7. 如果攻击者拿到一个有效的 JWT，他能在过期之前一直使用吗？
   本项目有什么机制可以提前让它失效？
   （提示：顶号检测原理）

8. JwtInterceptor 返回 401 时，直接写 HttpServletResponse 而不是抛异常——
   为什么？如果在拦截器中抛异常，GlobalException 能捕获到吗？
   （提示：Spring 异常处理的作用范围）

9. BaseUserServiceImpl.login() 中生成了一个 UUID 作为 loginVersion——
   这个 UUID 是如何一步步到达 JwtInterceptor 并被比对的？
   （提示：JWT Payload → Token 字符串 → 前端 Header → 拦截器解析）

10. @Auth({1, 2}) 和 @Auth({2, 3}) 的区别是什么？
    为什么没有 @Auth({1, 3}) 这种用法？（提示：项目实际业务场景）
```

---

## 5. 本课总结

### 核心记忆点

1. **JWT 三段式**：`Header.Payload.Signature`——Header 声明算法，Payload 存用户数据（不存敏感信息！），Signature 防篡改

2. **`@Auth` 注解**：
   ```java
   @Target({METHOD, TYPE})    // 可加在方法上或类上
   @Retention(RUNTIME)       // 运行时保留（反射能读）
   int[] value() default {};  // 空数组 = 只需要登录，非空 = 需要指定角色
   ```

3. **拦截器执行顺序（8 步）**：
   ```
   白名单 → HandlerMethod 检查 → 获取 @Auth → 提取 Token →
   解析 JWT → 设置 ThreadLocal → 顶号检测 → 角色校验 → 放行
   ```

4. **ThreadLocal 线程隔离**：
   - `UserContext.setUser()` → 写入当前线程
   - `UserContext.getUserId()` → 从当前线程读取
   - ⚠️ 必须 `clear()` —— 否则线程复用时会泄漏到下一个请求

5. **顶号检测三要素**：
   ```
   登录时：UUID → Redis（user:login_version:<userId>）+ JWT Payload
   请求时：JWT 中的 loginVer vs Redis 中的 loginVer
   不一致 → 401 "账号已在其他设备登录"
   ```

6. **前后端协作**：
   ```
   后端签发 JWT → 前端存 sessionStorage → Axios 自动注入 Header
   → 拦截器解析验证 → 401 时前端自动清 Token 跳转登录
   ```

### 下节课预告

第 6 课我们将深入学习 Redis 在项目中的另一大应用——邮箱验证码系统。包括验证码的生成与存储（5min TTL）、发送频率控制（60s cooldown + 每日 10 次限制）、Redis Hash 的数据结构选择、Lua 脚本的原子操作、以及受信设备 Cookie 机制——如何让常用设备跳过二次验证。

---

> 📌 本课属于「在线考试系统全栈课程」，完整课表见 `CURRICULUM.md`，学习路线见 `LEARNING_ROADMAP.md`。
