# 第 4 课：REST API 设计与分层架构

> ⏱ 预计时间：90 分钟（概念 30 min + 读代码 40 min + 练习 20 min）
>
> 📁 本课涉及文件：
> `Result.java` · `ResultCode.java` · `BusinessException.java` · `ValidationException.java` · `GlobalException.java` · `SubjectController.java` · `QuestionController.java` · `BaseUserController.java` · `StudentExamController.java` · `ExamRecordController.java` · DTO 类（`UserLoginDTO` / `UserRegisterDTO` / `StudentExamSubmitDTO`）· VO 类（`BaseUserVO` / `ExamPaperDetailVO` / `PageVO`）

---

## 1. 本课目标

学完这节课，你应该能：

- ✅ 说清 DTO、VO、Entity 三类对象的职责边界——什么时候用哪一个？
- ✅ 理解 `Result<T>` 统一响应体的设计模式——为什么所有 API 返回同样的 JSON 结构？
- ✅ 掌握 `@Valid` + Jakarta Validation 的参数校验链——校验失败后发生了什么？
- ✅ 画出异常处理的完整链路图：Controller → Service 抛异常 → GlobalException 捕获 → Result.fail() 返回
- ✅ 看懂任何 Controller 代码——从最简 SubjectController 到最复杂的 StudentExamController
- ✅ 理解 RESTful URL 设计——本项目实际用到的 URL 范式
- ✅ 自己新增一个带校验 + 异常处理的 CRUD 接口

---

## 2. 概念讲解

### 2.1 为什么要分三层对象？

打开项目的 `pojo/` 目录，你会看到三个子包：

```
pojo/
├── entity/     ← 数据库映射对象（8 个类）
├── dto/        ← 请求参数对象（17 个类）
└── vo/         ← 响应展示对象（11 个类）
```

**新手常犯的错误**：直接用 Entity 接收请求参数、直接用 Entity 返回给前端。

那么问题来了——**直接用 Entity 不行吗？** 我们来看一个具体例子：

```java
// Entity: BaseUser（数据库映射）
public class BaseUser {
    private Integer id;
    private String account;
    private String password;    // ⚠️ 密码！永远不能返回给前端
    private Integer role;
    // ...
}

// DTO: UserLoginDTO（登录请求参数）
public class UserLoginDTO {
    @NotBlank(message = "账号不能为空")
    private String account;      // 只需要账号
    @NotBlank(message = "密码不能为空")
    private String password;     // 只需要密码
}

// VO: BaseUserVO（返回给前端的用户信息）
public class BaseUserVO {
    private Integer id;
    private String account;
    private String username;
    private String email;        // 有邮箱
    private Integer role;
    // ⚠️ 没有 password！没有 createTime！
}
```

**三层对象各自的职责**：

| 类型 | 职责 | 生命周期 | 关键特征 |
|------|------|---------|---------|
| **Entity** | 与数据库表一一对应 | 只在 Service/Mapper 层流转 | 有 `@TableName` / `@TableId`；字段 = SQL 列 |
| **DTO** | 承载 HTTP 请求参数 | Controller 接收 → 传给 Service | 有 `@NotBlank` / `@NotNull` 等校验注解 |
| **VO** | 承载 HTTP 响应数据 | Service 返回 → Controller 序列化为 JSON | 只包含前端需要的字段；可能组合多个 Entity |

**如果不分层会怎样？**

```java
// ❌ 直接用 Entity 接收请求
@PostMapping("/login")
public Result<BaseUser> login(@RequestBody BaseUser entity) {
    // 问题 1：前端传了 id=999 → Entity 直接 set 了 → 可能被误用
    // 问题 2：无法加 @NotBlank 校验 → 空账号空密码也能进来
    // 问题 3：返回的 BaseUser 包含 password → 密码泄露！
}
```

```java
// ❌ 直接用 Entity 返回
public Result<BaseUser> getUser(Integer id) {
    BaseUser user = baseUserMapper.selectById(id);
    return Result.success(user);
    // password 字段出现在 JSON 里了！
    // 即使 @JsonIgnore 临时解决，但不同接口需要不同字段——你没法用一个 Entity 搞定所有场景
}
```

> 💡 **面试考点**：被问到"为什么 DTO/VO/Entity 要分开"，不要只背概念。直接说"**安全**（密码不泄露）+ **校验**（DTO 上声明约束）+ **灵活性**（VO 可以组合多个 Entity 的数据，比如 `ExamPaperDetailVO extends ExamPaper { List<ExamPaperQuestionVO> questions }`）"。

### 2.2 `Result<T>` 统一响应体

#### 设计动机

前端调用后端 API 时，需要知道三件事：

```
1. 请求成功了吗？       ← code 字段（200 = 成功）
2. 发生了什么？         ← message 字段（"操作成功" / "账号或密码错误"）
3. 数据是什么？         ← data 字段（可能是用户信息、列表、null）
```

如果每个接口返回不同的 JSON 格式，前端需要为每个接口写不同的解析逻辑。`Result<T>` 解决了这个问题——**所有接口返回相同的结构**。

#### 前端看到的 JSON

```json
// 成功（无数据）
{ "code": 200, "message": "操作成功", "timestamp": 1719000000000 }

// 成功（有数据）
{ "code": 200, "message": "操作成功", "data": { "id": 1, "account": "admin" }, "timestamp": 1719000000000 }

// 成功（带分页）
{ "code": 200, "message": "操作成功", "data": [...], "count": 408, "timestamp": 1719000000000 }

// 失败
{ "code": 500, "message": "账号或密码错误", "timestamp": 1719000000000 }

// 参数错误
{ "code": 400, "message": "账号不能为空", "timestamp": 1719000000000 }
```

#### 源码设计精读

```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)    // ① null 字段不出现在 JSON 中
public class Result<T> {
    private Integer code;
    private String message;
    private T data;        // ② 泛型 T——每种数据类型都能用
    private Integer count; // ③ 只在分页场景出现
    private Long timestamp;

    private Result() {     // ④ 所有构造函数都是 private
        this.timestamp = System.currentTimeMillis();
    }
    // ... 4 个 private 构造函数，各不相同
}
```

**四个关键设计决策**：

**① `@JsonInclude(Include.NON_NULL)`**：`count` 在非分页场景下是 null，加了这注解后就不会出现在返回的 JSON 中。前端看到 `count` 字段就知道这是分页结果，不需要额外的判断逻辑。

**② 泛型 `<T>`**：`Result<BaseUserVO>` → data 是用户对象；`Result<List<Subject>>` → data 是列表；`Result<Void>` → 没有 data。一套代码覆盖所有场景。

**③ 所有构造函数是 `private`**：外部不能 `new Result(...)`，只能通过静态工厂方法创建。这是**强制规范**——保证所有 Result 对象都经过预定义的构造逻辑。

**④ 失败响应不传 data**：注意 `fail()` 方法的构造函数只设置 code + message + timestamp，不会设置 data。失败时前端只关心错误消息，不需要 data。

#### 静态工厂方法一览

```java
// ========== 成功（5 个重载版本）==========
Result.success()                          // 无数据（如删除、更新）
Result.success(data)                      // 有数据
Result.success("自定义消息", data)         // 自定义消息
Result.success(data, count)               // 带分页
Result.success("自定义消息", data, count)  // 自定义消息 + 分页

// ========== 失败（4 个重载版本）==========
Result.fail(ResultCode.NOT_FOUND)         // 用预定义枚举
Result.fail("账号或密码错误")              // 自定义消息（默认 code=500）
Result.fail(404, "资源未找到")            // 自定义 code + 消息
Result.fail(ResultCode.BAD_REQUEST, "邮箱格式不正确") // 枚举 + 覆盖消息
```

5+4=9 个工厂方法覆盖了所有场景，但使用起来极其简单——Controller 中只需要一行 `return Result.success(data)`。

> 💡 **设计模式**：这是**静态工厂方法模式**，比直接 `new Result()` 有三个好处：① 方法名有语义（`success` vs `fail`）；② 可以返回子类型（泛型）；③ 不需要每次传重复参数（timestamp 自动生成）。

### 2.3 `ResultCode` 枚举

```java
@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),
    NOT_FOUND(404, "资源未找到"),
    UNAUTHORIZED(401, "无权限"),
    FORBIDDEN(403, "禁止访问"),
    BAD_REQUEST(400, "参数错误");
}
```

**为什么不直接写数字？**

```java
// ❌ 魔法数字——6 个月后你自己都不知道这个 400 是什么意思
throw new BusinessException("参数错误", 400);

// ✅ 语义清晰 + IDE 能自动补全
throw new BusinessException("参数错误", ResultCode.BAD_REQUEST.getCode());
```

注意：本项目用的是自定义枚举，没有直接用 `HttpStatus`，因为项目中有些 500 错误是业务异常（如"账号已存在"），和 HTTP 层面的 500 Internal Server Error 含义不同。

### 2.4 异常处理链——三层协作

这是本课最重要的一个知识点。先看完整流程图：

```
HTTP 请求到达 Controller
    │
    ├─ Controller 方法执行
    │   ├─ @Valid 校验 DTO → 失败 → 抛出 MethodArgumentNotValidException
    │   │                                          ↓
    │   │                              @ExceptionHandler 捕获 → Result.fail(400, "账号不能为空")
    │   │
    │   └─ 调用 Service 方法
    │       └─ Service 业务逻辑
    │           ├─ 用户名不存在 → throw new BusinessException("账号或密码错误")
    │           │                        ↓
    │           │           @ExceptionHandler 捕获 → Result.fail(500, "账号或密码错误")
    │           │
    │           ├─ 参数不合法 → throw new ValidationException("参数不合法")
    │           │                        ↓
    │           │           @ExceptionHandler 捕获 → Result.fail(400, "参数不合法")
    │           │
    │           └─ 未知错误 → throw new RuntimeException("...")
    │                                    ↓
    │                       @ExceptionHandler(Exception.class) 兜底
    │                       → Result.fail(500, "系统繁忙，请稍后再试")
    │
    └─ 正常返回 → Result.success(data)
```

**四层异常处理器**：

```java
@Slf4j
@RestControllerAdvice        // ← 全局拦截所有 Controller 的异常
public class GlobalException {

    // 第 1 层：@Valid 参数校验失败（最具体）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(...) {
        // 从 BindingResult 中提取第一条错误消息
        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(400, message);
        // 前端收到: { "code": 400, "message": "账号不能为空" }
    }

    // 第 2 层：自定义 ValidationException
    @ExceptionHandler(ValidationException.class)
    public Result<Void> handleValidationException(ValidationException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 第 3 层：业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 第 4 层：未知异常（兜底）
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常：{}", e.getMessage());  // 记日志！
        return Result.fail(500, "系统繁忙，请稍后再试");
        // ⚠️ 不暴露真实错误信息给前端（安全考虑）
    }
}
```

**Spring 匹配 `@ExceptionHandler` 的优先级**：

```
最具体的异常类型优先匹配
  MethodArgumentNotValidException  → Handler 1
  ValidationException               → Handler 2（如果没有则走 3）
  BusinessException                 → Handler 3（如果没有则走 4）
  RuntimeException / Exception      → Handler 4（兜底）
```

> 💡 **为什么 Handler 4 返回"系统繁忙"而不返回真实错误？** 安全考虑。如果返回 `e.getMessage()`（比如 "Table 'exam.user' doesn't exist"），攻击者就知道了你的数据库结构。

### 2.5 `@Valid` + Jakarta Validation——声明式参数校验

看这个 DTO：

```java
@Data
public class UserRegisterDTO {
    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 30, message = "账号长度必须在3-30")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度必须在6-30")
    private String password;

    @NotNull(message = "请选择用户角色")
    @Min(value = 1, message = "用户角色不正确")
    @Max(value = 2, message = "用户角色不正确")
    private Integer role;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

Controller 中只需要加一个 `@Valid` 注解：

```java
@PostMapping("/register")
public Result<UserLoginResponseVO> register(
        @Valid @RequestBody UserRegisterDTO dto) {  // ← @Valid 触发校验
    return Result.success(emailService.beginRegister(dto));
}
```

**校验执行流程**：

```
1. Spring 收到 POST /user/register
2. 反序列化 JSON → UserRegisterDTO 对象
3. @Valid 触发 Jakarta Validation
4. 遍历 DTO 中所有校验注解：
   - account 不为空？长度 3-30？      → 不满足 → 记录错误消息
   - password 不为空？长度 6-30？     → 不满足 → 记录错误消息
   - role 不为 null？范围 1-2？       → 不满足 → 记录错误消息
   - email 不为空？格式正确？         → 不满足 → 记录错误消息
5. 有任何错误 → 抛出 MethodArgumentNotValidException("账号不能为空")
6. GlobalException.handleMethodArgumentNotValidException() 捕获
7. 取出第一条错误消息 → Result.fail(400, "账号不能为空")
```

> 💡 **为什么只取第一条错误？** `findFirst()` 取第一条，不把所有错误全返回。这是 UX 考虑——用户一次只能纠正一个问题，逐条提示比同时看到 5 条错误体验更好。

**本项目用到的校验注解**：

| 注解 | 含义 | 用于 |
|------|------|------|
| `@NotBlank` | 非 null 且非空字符串（trim 后） | 账号、密码、用户名 |
| `@NotNull` | 非 null（不检查内容） | role、枚举类型 |
| `@Size(min, max)` | 字符串长度范围 | 账号、密码 |
| `@Min` / `@Max` | 数值范围 | role (1-2) |
| `@Email` | 邮箱格式 | email |

### 2.6 手动抛异常 vs `@Valid` 自动校验

Controller 中有两种错误处理方式：

```java
// 方式 1：@Valid 自动校验（推荐！）
@PostMapping("/register")
public Result<UserLoginResponseVO> register(@Valid @RequestBody UserRegisterDTO dto) {
    // 如果到这里，说明所有校验都通过了——不需要再写 if 判断
    return Result.success(emailService.beginRegister(dto));
}

// 方式 2：手动抛异常（业务校验必须这样）
@PostMapping
public Result<Void> add(@RequestBody Subject subject) {
    if (!StringUtils.hasText(subject.getName())) {
        throw new BusinessException("科目名称不能为空");
        // ↑ 这种不是简单的格式校验，需要查数据库或做复杂判断时手动抛
    }
    subjectService.save(subject);
    return Result.success();
}
```

**选择原则**：

| 场景 | 用什么 |
|------|--------|
| 字段格式校验（非空、长度、范围、邮箱格式） | DTO 上加注解 + `@Valid` |
| 数据库相关的业务校验（账号是否已存在、试卷是否已发布） | Service 中 `throw new BusinessException(...)` |
| 权限校验 | `@Auth` 注解（第 5 课讲） |

### 2.7 RESTful URL 设计——本项目实际规范

REST 理论上有一套严格的 URL 设计规范。但在真实项目中，严格 REST 往往不够灵活。本项目的 URL 设计是**实用主义的 REST**。

#### 本项目使用的 URL 模式

```
GET    /subject/list              ← 列表（不分页）
GET    /subject/listPage          ← 列表（分页）
GET    /subject/{id}              ← 详情
POST   /subject                   ← 新增
PUT    /subject                   ← 修改（传 id 在 body 里）
DELETE /subject/{id}              ← 删除

POST   /user/login                ← 动作型（不是资源 CRUD）
POST   /user/register             ← 动作型
PUT    /user/{id}/updatePassword  ← 子资源操作
PUT    /user/uploadAvatar         ← 动作型

POST   /student/examRecords/start    ← 动作型（开始考试）
POST   /student/examRecords/submit   ← 动作型（提交考试）
POST   /student/examRecords/warn     ← 动作型（切屏警告）
GET    /student/examRecords/listPage ← 分页列表
```

#### 关键规律

1. **路径用复数名词**：`/subject` 而非 `/subjects`（本项目用的是单数，两种都可以，关键是一致）
2. **动作用 HTTP 方法表示**：GET=查, POST=增, PUT=改, DELETE=删
3. **动作型操作也用 POST**：`/login`、`/register`、`/start`、`/submit`——这些不是标准 CRUD，用 POST 最合适，后面跟动词
4. **子资源挂载**：`/student/examRecords/start`——考试记录是学生的子资源，URL 反映了这种从属关系
5. **分页用 `listPage`**：区别于 `list`（全量返回），避免意外查询大量数据

> 💡 **面试沟通**：当面试官问"你们 REST 设计遵循了哪些约定"，不要硬套理论。说"我们采用的是**实用主义 REST**——核心资源的 CRUD 走标准 HTTP 方法，动作型操作（登录、提交考试）用 POST + 动词后缀。最重要的是整个项目 URL 风格保持一致。"

### 2.8 三层架构的数据流

```
┌──────────────────────────────────────────────────────────┐
│ Controller 层（接收请求，返回响应）                          │
│                                                            │
│  @PostMapping("/register")                                 │
│  public Result<...> register(@Valid @RequestBody UserRegisterDTO dto) {
│      return Result.success(emailService.beginRegister(dto));
│  }                                │                        │
│      DTO 进来 ─────────────────────┘                        │
│      VO 出去 ←── Result.success(vo)                        │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│ Service 层（业务逻辑）                                      │
│                                                            │
│  public UserLoginResponseVO beginRegister(UserRegisterDTO dto) {
│      // 1. 检查账号是否已存在                                │
│      BaseUser existing = this.getOne(                       │
│          new LambdaQueryWrapper<BaseUser>()                  │
│              .eq(BaseUser::getAccount, dto.getAccount())     │
│      );                                                     │
│      if (existing != null) {                                │
│          throw new BusinessException("账号已存在");           │
│      }                                                      │
│      // 2. Entity ← DTO 转换                                │
│      BaseUser entity = new BaseUser();                      │
│      BeanUtils.copyProperties(dto, entity);                 │
│      // 3. 持久化                                            │
│      this.save(entity);                                     │
│      // 4. Entity → VO 转换                                 │
│      return buildLoginResponseVO(entity);                   │
│  }                                                          │
└──────────────────────┬───────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────┐
│ Mapper 层（数据访问）                                       │
│                                                            │
│  public interface BaseUserMapper extends BaseMapper<BaseUser> {
│      // 空的！CRUD 由 MyBatis-Plus 自动提供                   │
│  }                                                          │
│                                                            │
│  → INSERT INTO user (account, password, ...) VALUES (...)  │
│  → SELECT * FROM user WHERE account = ?                    │
└──────────────────────────────────────────────────────────┘
```

**数据在不同层之间的转换**：

```
HTTP 请求 JSON  →  DTO  →  Entity  →  DB 行
                                 ↓
HTTP 响应 JSON  ←  VO   ←  Entity  ←  DB 行
```

转换发生在 Service 层——Controller 只负责"接收 DTO，返回 Result<VO>"，不碰 Entity。

---

## 3. 代码阅读（guided walkthrough）

### 3.1 从最简单的 Controller 开始：SubjectController

```
controller/SubjectController.java
```

这是全项目最标准的 CRUD Controller，只有 70 行代码，涵盖了所有基础模式。

**逐方法解读**：

```java
@RestController              // = @Controller + @ResponseBody（每个方法返回 JSON）
@RequestMapping("/subject")   // 所有接口的 URL 前缀
@RequiredArgsConstructor     // Lombok：为 final 字段生成构造函数（依赖注入）
public class SubjectController {
    private final SubjectService subjectService;  // ← Spring 自动注入
```

**① 查询列表（不分页 —— 用于下拉框等场景）**：

```java
@GetMapping("/list")
@Auth({2, 3})    // 教师和管理员可访问
public Result<List<Subject>> list() {
    return Result.success(
        subjectService.list(
            new LambdaQueryWrapper<Subject>()
                .orderByDesc(Subject::getCreateTime)
        )
    );
}
```

返回格式：
```json
{ "code": 200, "message": "操作成功", "data": [{ "id": 1, "name": "数据结构" }, ...] }
```

**② 分页查询**：

```java
@GetMapping("/listPage")
@Auth(3)    // 只有管理员
public Result<PageVO<Subject>> listPage(SubjectQueryDTO query) {
    Page<Subject> page = subjectService.page(
        Page.of(query.getPageNum(), query.getPageSize()),
        new LambdaQueryWrapper<Subject>()
            .like(StringUtils.hasText(query.getName()), Subject::getName, query.getName())
            .orderByDesc(Subject::getCreateTime)
    );
    return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
}
```

**关键观察**：`.like(StringUtils.hasText(query.getName()), ...)`——参数 `condition` 为 false 时，`.like()` 不生效。这避免了在 Controller 中写 if-else 判断"这个参数有没有传"。

**③ 新增（手动校验）**：

```java
@PostMapping
@Auth(3)
public Result<Void> add(@RequestBody Subject subject) {
    if (!StringUtils.hasText(subject.getName())) {
        throw new BusinessException("科目名称不能为空");
    }
    subject.setId(null);          // 防止前端传 id 覆盖
    subject.setCreateTime(LocalDateTime.now());
    subjectService.save(subject);
    return Result.success();      // 无数据返回
}
```

**④ 修改**：

```java
@PutMapping
@Auth(3)
public Result<Void> update(@RequestBody Subject subject) {
    subjectService.updateById(subject);  // 根据 id 更新，null 字段不更新
    return Result.success();
}
```

**⑤ 删除**：

```java
@DeleteMapping("/{id}")
@Auth(3)
public Result<Void> delete(@PathVariable Integer id) {
    subjectService.removeById(id);
    return Result.success();
}
```

**观察规律**：新增/修改/删除全部返回 `Result<Void>`——不需要返回数据，前端只看 `code === 200` 判断成功。

### 3.2 加了校验的 Controller：BaseUserController

```
controller/BaseUserController.java
```

这里我们只看两个方法，重点是观察 `@Valid` 如何工作。

**登录接口**：

```java
@PostMapping("/login")
public Result<UserLoginResponseVO> login(
        @Valid @RequestBody UserLoginDTO userLoginDTO,  // ← @Valid 校验
        HttpServletRequest request) {
    // 如果到了这里，说明 account 和 password 的校验已通过
    // 不需要再手动 if (account == null || password == null)
    Map<Integer, String> trustedDeviceTokens = extractTrustedDeviceTokens(request);
    UserLoginResponseVO vo = emailService.beginLogin(userLoginDTO, trustedDeviceTokens);
    return Result.success(vo);
}
```

对应的 DTO：

```java
public class UserLoginDTO {
    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 30, message = "账号长度必须在3-30")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

**注册接口**：

```java
@PostMapping("/register")
public Result<UserLoginResponseVO> register(
        @Valid @RequestBody UserRegisterDTO dto) {
    return Result.success(emailService.beginRegister(dto));
}
```

对应的 DTO 有 6 个校验注解（见上文 2.5 节）。

**修改密码接口**：

```java
@PutMapping("/{id}/updatePassword")
private Result<Void> updatePassword(
        @PathVariable Integer id,                        // 从 URL 路径中取值
        @Valid @RequestBody UserUpdatePasswordDTO dto) { // 从请求体取值 + 校验
    baseUserService.updatePassword(id, dto);
    return Result.success();
}
```

**`@PathVariable` vs `@RequestParam` vs `@RequestBody`**：

| 注解 | 数据来源 | 示例 |
|------|---------|------|
| `@PathVariable` | URL 路径中 | `/user/5/updatePassword` → id=5 |
| `@RequestParam` | URL 查询参数 | `/examRecords/start?paperId=1` → paperId=1 |
| `@RequestBody` | HTTP 请求体（JSON） | `{ "account": "admin", "password": "123456" }` |

### 3.3 有类级别鉴权的 Controller：QuestionController

```
controller/QuestionController.java
```

```java
@RestController
@RequestMapping("/question")
@RequiredArgsConstructor
@Auth({2, 3})              // ← 类级别鉴权：所有方法都需要教师或管理员
public class QuestionController {
```

注意 `@Auth({2, 3})` 在类上——这意味着这个 Controller 的**所有方法**都需要教师或管理员权限。如果某个方法需要不同权限，可以在方法上覆盖。

**带多条件查询的分页**：

```java
@GetMapping("/listPage")
public Result<PageVO<Question>> listPage(QuestionQueryDTO query) {
    Page<Question> page = questionService.page(
        Page.of(query.getPageNum(), query.getPageSize()),
        new LambdaQueryWrapper<Question>()
            .eq(query.getSubjectId() != null, Question::getSubjectId, query.getSubjectId())
            .eq(query.getType() != null, Question::getType, query.getType())
            .eq(query.getDifficulty() != null, Question::getDifficulty, query.getDifficulty())
            .orderByDesc(Question::getCreateTime)
    );
    return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
}
```

**对比 SubjectController 的 `.like(StringUtils.hasText(...))`**，这里用的是 `.eq(query.getXxx() != null, ...)`。两种写法实现的意义一致——条件为 false 时跳过该过滤。

### 3.4 业务最密集的 Controller：StudentExamController

```
controller/StudentExamController.java
```

这是全项目代码量最大的 Controller（515 行），涵盖了考试全生命周期。我们并不需要逐行读完——按**功能分组**理解：

```java
@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
@Auth(1)              // 所有方法都需要学生角色
public class StudentExamController {
    // 注入了 7 个 Service——这是业务密集型 Controller 的典型特征
    private final ExamPaperService examPaperService;
    private final ExamRecordService examRecordService;
    private final ExamRecordAnswerService examRecordAnswerService;
    private final QuestionService questionService;
    private final BaseUserService baseUserService;
    private final WrongQuestionService wrongQuestionService;
    private final ExamPaperQuestionService examPaperQuestionService;
```

**按功能分组（8 组接口）**：

```
组 1 — 试卷查询
  GET  /student/examPapers/listPage       ← 查看可用试卷
  GET  /student/examPapers/{id}/detail     ← 查看试卷详情（含题目）

组 2 — 考试生命周期
  POST /student/examRecords/start          ← 开始考试（创建/续考记录）
  POST /student/examRecords/submit         ← 提交考试（自动判分+错题入库）
  POST /student/examRecords/save-progress  ← 中途保存进度（不交卷）
  GET  /student/examRecords/{id}/draft      ← 恢复草稿

组 3 — 防作弊
  POST /student/examRecords/warn           ← 上报切屏警告

组 4 — 考试记录
  GET  /student/examRecords/listPage       ← 查看我的考试记录
  GET  /student/examRecords/{id}/detail    ← 查看成绩详情

组 5 — 错题集
  GET    /student/wrongQuestions/listPage  ← 查看我的错题
  PUT    /student/wrongQuestions/{id}/mastered ← 标记已掌握
  DELETE /student/wrongQuestions/{id}      ← 删除错题
```

**重点看两个核心方法**：

**startExam——开始考试的复杂逻辑**：

```java
@PostMapping("/examRecords/start")
public Result<ExamRecord> start(@RequestParam Integer paperId) {
    Integer userId = UserContext.getUserId();  // 从 ThreadLocal 获取当前用户
    BaseUser user = baseUserService.getById(userId);
    ExamPaper paper = examPaperService.getById(paperId);

    // 校验 1：试卷是否可参加
    if (paper == null || !Objects.equals(paper.getStatus(), 1)) {
        throw new BusinessException("试卷不可参加");
    }

    // 校验 2：是否有进行中的考试（直接返回续考）
    ExamRecord existing = examRecordService.getOne(
        new LambdaQueryWrapper<ExamRecord>()
            .eq(ExamRecord::getUserId, userId)
            .eq(ExamRecord::getPaperId, paperId)
            .last("limit 1")
    );
    if (existing != null) {
        if (Objects.equals(existing.getStatus(), 0)) {
            return Result.success(existing);  // 进行中 → 直接返回续考
        }
        // 校验 3：考试次数是否已用完
        if (attempted >= maxAttempts) {
            throw new BusinessException("考试次数已用完");
        }
        // 重考：覆盖旧记录
        examRecordAnswerService.remove(...);  // 清除旧答题明细
        existing.setAttemptCount(attempted + 1);
        // ... 重置所有字段
        examRecordService.updateById(existing);
        return Result.success(existing);
    }
    // 校验 4：首次考试 → 创建新记录
    ExamRecord record = new ExamRecord();
    // ... 设置所有字段
    examRecordService.save(record);
    return Result.success(record);
}
```

**关键观察**：业务校验全部放在 Controller 中（如试卷状态检查、次数检查），然后调用 Service 执行。这是本项目的特点——简单场景下 Controller 直接处理校验，复杂场景下沉到 Service。

**submit——提交 + 自动判分 + 错题入库**：

```java
@PostMapping("/examRecords/submit")
public Result<Void> submit(@RequestBody StudentExamSubmitDTO dto) {
    Integer userId = UserContext.getUserId();
    ExamRecord record = examRecordService.getById(dto.getRecordId());

    // 校验：记录存在且是本人的
    if (record == null || !Objects.equals(record.getUserId(), userId)) {
        throw new BusinessException("考试记录不存在");
    }

    // 后端时间校验：防止前端绕过倒计时
    LocalDateTime deadline = record.getStartTime().plusMinutes(paper.getDuration());
    if (LocalDateTime.now().isAfter(deadline)) {
        throw new BusinessException("考试时间已结束，无法提交");
    }

    // 清除旧答题明细（重考场景）
    examRecordAnswerService.remove(...);

    // 逐题判分
    for (var answerDTO : dto.getAnswers()) {
        Question question = questionService.getById(answerDTO.getQuestionId());
        // ... 判断对错
        boolean correct = objective && normalizeAnswer(question.getAnswer())
                           .equals(normalizeAnswer(answerDTO.getUserAnswer()));
        // 答错 → 录入错题集
        if (objective && !correct) {
            saveWrongQuestion(userId, question, answerDTO.getUserAnswer());
        }
    }
    // 更新总分 + 历史最高分
    record.setScore(totalScore);
    record.setStatus(1);  // 标记已交卷
    examRecordService.updateById(record);
    return Result.success();
}
```

**关键观察**：

1. **`UserContext.getUserId()`**：当前用户 ID 来自拦截器设置的 ThreadLocal，不需要前端传（防止伪造）
2. **后端时间校验**：即使前端有倒计时，后端也重新计算一次——**永远不信任客户端**
3. **答案归一化**：`normalizeAnswer()` 去空格、排序、统一分隔符 → "A,B,C" 和 "B, A, C" 被判为相同

### 3.5 ExamRecordController——管理员端的考试记录管理

```
controller/ExamRecordController.java
```

结构类似，一个亮点是**级联删除**：

```java
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Integer id) {
    // 先删答题明细
    examRecordAnswerService.remove(
        new LambdaQueryWrapper<ExamRecordAnswer>()
            .eq(ExamRecordAnswer::getRecordId, id)
    );
    // 再删考试记录
    examRecordService.removeById(id);
    return Result.success();
}
```

没有使用数据库的 `CASCADE DELETE`（外键级联删除），而是在代码中手动控制顺序。好处是删除逻辑显式可见，方便加日志、审计等逻辑。

### 3.6 阅读 VO 的继承模式

```
pojo/vo/
├── ExamPaperDetailVO extends ExamPaper { List<ExamPaperQuestionVO> questions; }
├── ExamRecordDetailVO extends ExamRecord { List<ExamRecordAnswer> answers; }
└── BaseUserVO (独立定义，不继承 BaseUser，不包含 password)
```

**两种 VO 设计模式**：

| 模式 | 示例 | 适用场景 |
|------|------|---------|
| **继承 Entity** | `ExamPaperDetailVO extends ExamPaper` | 需要在 Entity 基础上加嵌套数据（题目列表、答案明细） |
| **独立定义** | `BaseUserVO`（不继承 `BaseUser`） | 需要**隐藏**某些字段（如 password） |

### 3.7 阅读 PageVO——通用分页容器

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
    private List<T> records;   // 当前页数据
    private Long total;        // 总记录数
}
```

这是最简洁的 VO。在 Controller 中：
```java
return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
```

返回 JSON：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [{ "id": 1, "name": "数据结构" }, ...],
    "total": 10
  },
  "timestamp": 1719000000000
}
```

---

## 4. 动手练习

### 练习 1：新增一个完整 CRUD Controller（25 min）

**目标**：模仿 `SubjectController`，为 `exam_paper` 表新增一个简易的教师端 Controller。

**步骤 1**：创建 `ExamPaperSimpleController.java`（放在 `controller/` 包下）：

```java
@RestController
@RequestMapping("/examPaper")
@RequiredArgsConstructor
@Auth({2, 3})
public class ExamPaperSimpleController {
    private final ExamPaperService examPaperService;

    // 1. 分页查询
    @GetMapping("/listPage")
    public Result<PageVO<ExamPaper>> listPage(ExamPaperQueryDTO query) {
        Page<ExamPaper> page = examPaperService.page(
            Page.of(query.getPageNum(), query.getPageSize()),
            new LambdaQueryWrapper<ExamPaper>()
                .like(StringUtils.hasText(query.getTitle()), ExamPaper::getTitle, query.getTitle())
                .orderByDesc(ExamPaper::getCreateTime)
        );
        return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
    }

    // 2. 查询详情
    @GetMapping("/{id}")
    public Result<ExamPaper> detail(@PathVariable Integer id) {
        return Result.success(examPaperService.getById(id));
    }

    // 3. 新增试卷（带手动校验）
    @PostMapping
    public Result<Void> add(@RequestBody ExamPaper paper) {
        if (!StringUtils.hasText(paper.getTitle())) {
            throw new BusinessException("试卷标题不能为空");
        }
        if (paper.getSubjectId() == null) {
            throw new BusinessException("请选择科目");
        }
        paper.setId(null);
        paper.setStatus(0);  // 默认未发布
        paper.setCreateTime(LocalDateTime.now());
        examPaperService.save(paper);
        return Result.success();
    }

    // 4. 删除（含答题明细清理——提示：删除试卷前要确认没有被考试记录引用）
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        examPaperService.removeById(id);
        return Result.success();
    }
}
```

**步骤 2**：用 curl 或 Swagger 测试：
```bash
# 新增试卷
curl -X POST http://localhost:8077/api/examPaper \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <你的token>" \
  -d '{"title":"数据结构期中考试","subjectId":1,"totalScore":100,"duration":120}'

# 分页查询
curl http://localhost:8077/api/examPaper/listPage?pageNum=1\&pageSize=10 \
  -H "Authorization: Bearer <你的token>"
```

**验证**：确认新增后能查到该试卷，且 `status` 为 0（未发布）。

---

### 练习 2：给 DTO 加校验（15 min）

**目标**：为练习 1 中的新增试卷逻辑创建专门的 DTO 并添加 `@Valid` 校验。

**步骤 1**：创建 `ExamPaperCreateDTO.java`（放在 `dto/` 包下）：

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExamPaperCreateDTO {
    @NotBlank(message = "试卷标题不能为空")
    @Size(min = 2, max = 100, message = "标题长度2-100")
    private String title;

    @NotNull(message = "请选择科目")
    private Integer subjectId;

    @NotNull(message = "请设置总分")
    @Min(value = 1, message = "总分必须大于0")
    @Max(value = 1000, message = "总分不能超过1000")
    private Integer totalScore;

    @NotNull(message = "请设置考试时长")
    @Min(value = 1, message = "时长必须大于0分钟")
    @Max(value = 480, message = "时长不能超过480分钟")
    private Integer duration;
}
```

**步骤 2**：修改 Controller 的 `add` 方法：

```java
@PostMapping
public Result<Void> add(@Valid @RequestBody ExamPaperCreateDTO dto) {
    ExamPaper paper = new ExamPaper();
    BeanUtils.copyProperties(dto, paper);
    paper.setStatus(0);
    paper.setCreateTime(LocalDateTime.now());
    examPaperService.save(paper);
    return Result.success();
}
```

对比改造前后：改造前需要在 Controller 中手动写 `if (!StringUtils.hasText(...))`，改造后这些校验被移到 DTO 的注解上，Controller 变得更干净。

---

### 练习 3：自测题

```
1. DTO、VO、Entity 的分工是什么？如果在 BaseUser Entity 上直接加 @JsonIgnore
   隐藏 password，为什么还不够好？
   （提示：不同接口需要不同的字段子集）

2. Result<T> 中 @JsonInclude(Include.NON_NULL) 的作用是什么？
   （提示：哪个字段在非分页场景下是 null）

3. @Valid 校验失败后，异常是如何一步步变成 JSON 错误响应的？
   （提示：MethodArgumentNotValidException → GlobalException → Result.fail）

4. 为什么 GlobalException 的兜底处理器返回"系统繁忙，请稍后再试"而不是 e.getMessage()？
   （提示：安全考虑）

5. 在 StudentExamController.submit() 中，后端重新算了考试截止时间——
   为什么前端已经有倒计时了，后端还要再算一次？
   （提示：用户可能做了什么）

6. SubjectController 中 .like(StringUtils.hasText(name), Subject::getName, name)
   的第一个参数有什么作用？
   （提示：如果 name 为空字符串...）

7. Result<Void> 中的 Void 是什么意思？为什么新增/修改/删除返回这个？
   （提示：泛型参数，表示不需要携带数据）

8. 以下 URL 中哪些是本项目的实际风格？哪些不符合？
   a) GET  /subject/listPage
   b) POST /subject/create       （提示：REST 语义）
   c) POST /student/examRecords/start
   d) DELETE /subject/5/delete   （提示：冗余）
```

---

## 5. 本课总结

### 核心记忆点

1. **三层对象各司其职**：
   - **DTO**（Data Transfer Object）：接收请求参数，加 `@Valid` 校验注解
   - **Entity**：映射数据库表，只在 Service/Mapper 层使用
   - **VO**（View Object）：返回给前端，只含前端需要的字段，可能组合多个 Entity

2. **统一响应三层协作**：
   ```
   Controller → 返回 Result.success(vo)
                 ↓ 错误时
   Service   → throw new BusinessException("...")
                 ↓ 被捕获
   GlobalException → Result.fail(code, message)
   ```

3. **异常处理四层优先级**：
   `MethodArgumentNotValidException` → `ValidationException` → `BusinessException` → `Exception`（兜底）

4. **Controller 代码的标准模板**：
   ```java
   @RestController
   @RequestMapping("/xxx")
   @RequiredArgsConstructor
   @Auth({...})
   public class XxxController {
       private final XxxService xxxService;

       @GetMapping("/listPage")
       public Result<PageVO<Xxx>> listPage(XxxQueryDTO query) {
           Page<Xxx> page = xxxService.page(...);
           return Result.success(new PageVO<>(page.getRecords(), page.getTotal()));
       }

       @PostMapping
       public Result<Void> add(@Valid @RequestBody XxxDTO dto) {
           // 转换 + 保存
           return Result.success();
       }
   }
   ```

5. **RESTful URL 实用规范**：
   - CRUD：`GET /listPage` · `GET /{id}` · `POST` · `PUT` · `DELETE /{id}`
   - 动作型：`POST /xxx/start` · `POST /xxx/submit`

### 下节课预告

第 5 课我们将深入学习认证鉴权系统——JWT 的 Header + Payload + Signature 结构、`@Auth` 自定义注解如何通过反射读取、`JwtInterceptor` 的拦截全流程、`UserContext`（ThreadLocal）的线程隔离原理、以及 Redis 顶号检测——当你在另一台设备登录时，旧的 Token 如何被"踢下线"。

---

> 📌 本课属于「在线考试系统全栈课程」，完整课表见 `CURRICULUM.md`，学习路线见 `LEARNING_ROADMAP.md`。
