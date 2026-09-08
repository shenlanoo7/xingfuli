# 幸福里（HM DianPing）

一个基于 Spring Boot、MySQL 与 Redis 的本地生活点评后端项目，包含商铺查询、短信验证码登录、探店笔记、关注与 Feed 流、签到、优惠券秒杀等功能。项目重点演示 Redis 在缓存、分布式锁、地理位置检索、消息队列和高并发下单等场景中的应用。

> 本仓库只包含后端代码与数据库初始化脚本，不包含前端工程。

## 功能概览

- 手机号验证码登录：验证码和登录态存储在 Redis，访问时自动刷新 Token 有效期
- 商铺服务：分类/名称查询、详情缓存、缓存逻辑过期与异步重建
- 附近商铺：使用 Redis GEO 按距离检索并分页
- 探店社区：发布笔记、点赞排行、关注关系、共同关注、滚动分页 Feed
- 用户签到：使用 Redis Bitmap 记录签到并统计连续签到天数
- 优惠券秒杀：Lua 原子校验库存与一人一单，Redis Stream 异步创建订单
- 分布式能力：Redis 全局 ID、Redisson 分布式锁、缓存穿透与缓存击穿处理

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 基础框架 | Java 8、Spring Boot 2.7.4、Spring MVC |
| 数据访问 | MyBatis-Plus 3.5.2、MySQL |
| 缓存与消息 | Spring Data Redis、Lettuce、Redis Stream |
| 分布式锁 | Redisson 3.17.7 |
| 工具 | Hutool 5.8.8、Lombok、Maven |

## 运行环境

- JDK 8+
- Maven 3.6+
- MySQL 5.7+ 或 8.x
- Redis 6.2+（附近商铺查询使用 `GEOSEARCH`）

## 快速开始

### 1. 获取代码

```bash
git clone git@github.com:shenlanoo7/xingfuli.git
cd xingfuli
```

### 2. 初始化 MySQL

创建数据库并导入仓库根目录的 [hmdp.sql](./hmdp.sql)：

```sql
CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

```bash
mysql -u root -p hmdp < hmdp.sql
```

初始化脚本会创建用户、商铺、笔记、关注、优惠券和订单等 11 张业务表，并写入演示数据。

### 3. 修改连接配置

编辑 [application.yaml](./src/main/resources/application.yaml)，将 MySQL 与 Redis 地址、用户名和密码替换为自己的环境配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC
    username: root
    password: your_mysql_password
  redis:
    host: 127.0.0.1
    port: 6379
    password: your_redis_password # Redis 无密码时删除此项或留空
```

不要将真实密码提交到版本库；生产或共享环境建议通过外部配置注入凭据。

### 4. 初始化 Redis Stream

应用启动后会立即以 `g1` 消费组读取 `stream.orders`。首次运行前执行：

```bash
redis-cli XGROUP CREATE stream.orders g1 0 MKSTREAM
```

如果 Redis 设置了密码，请为 `redis-cli` 补充相应的认证参数。若提示 `BUSYGROUP Consumer Group name already exists`，说明消费组已经创建，可以忽略。

### 5. 启动应用

```bash
mvn spring-boot:run
```

也可以先打包再运行：

```bash
mvn clean package -DskipTests
java -jar target/hmdp-1.0-SNAPSHOT.jar
```

服务默认监听 `http://localhost:8081`。

## Redis 数据预热

部分功能依赖预先写入 Redis 的索引或缓存。请先完成 MySQL、Redis 和 Stream 初始化，然后执行对应测试方法：

```bash
# 预热店铺详情的逻辑过期缓存
mvn -Dtest=RedisTest#testSaveShop test

# 建立附近商铺所需的 GEO 索引
mvn -Dtest=RedisTest#testLoadShopData test
```

说明：

- `GET /shop/{id}` 当前使用逻辑过期策略；未预热 `cache:shop:{id}` 时会返回“店铺不存在”。
- 携带坐标调用 `GET /shop/of/type` 前，需要先建立 `shop:geo:{typeId}` 索引。
- 通过 `POST /voucher/seckill` 新增秒杀券时，会自动把库存写入 `seckill:stock:{voucherId}`。

## 登录与调用示例

本项目未接入真实短信服务。请求验证码后，六位验证码会输出到应用的 DEBUG 日志中。

```bash
# 1. 获取验证码
curl -X POST 'http://localhost:8081/user/code?phone=13800138000'

# 2. 使用日志中的验证码登录，响应 data 字段即 Token
curl -X POST 'http://localhost:8081/user/login' \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000","code":"123456"}'

# 3. 访问需要登录的接口
curl 'http://localhost:8081/user/me' \
  -H 'authorization: 登录接口返回的Token'
```

除登录、热门笔记、商铺、商铺类型、上传和优惠券相关接口外，其余接口默认要求请求头携带 `authorization`。

## 主要接口

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 用户 | `POST /user/code` | 获取登录验证码 |
| 用户 | `POST /user/login` | 手机号验证码登录 |
| 用户 | `GET /user/me` | 获取当前用户 |
| 用户 | `POST /user/sign` | 当日签到 |
| 用户 | `GET /user/sign/count` | 查询连续签到天数 |
| 商铺 | `GET /shop/{id}` | 查询商铺详情 |
| 商铺 | `GET /shop/of/type` | 按类型或距离查询商铺 |
| 商铺 | `GET /shop/of/name` | 按名称搜索商铺 |
| 笔记 | `GET /blog/hot` | 查询热门笔记 |
| 笔记 | `POST /blog` | 发布笔记 |
| 笔记 | `PUT /blog/like/{id}` | 点赞或取消点赞 |
| 笔记 | `GET /blog/of/follow` | 查询关注用户的 Feed |
| 关注 | `PUT /follow/{id}/{isFollow}` | 关注或取关用户 |
| 关注 | `GET /follow/common/{id}` | 查询共同关注 |
| 优惠券 | `GET /voucher/list/{shopId}` | 查询商铺优惠券 |
| 优惠券 | `POST /voucher/seckill` | 新增秒杀券并初始化库存 |
| 秒杀 | `POST /voucher-order/seckill/{id}` | 抢购秒杀券 |

所有接口统一返回 `Result` 结构：

```json
{
  "success": true,
  "data": {}
}
```

失败响应会返回 `success: false` 和 `errorMsg`；分页响应还可能包含 `total`。配置已忽略值为 `null` 的 JSON 字段。

## 项目结构

```text
.
├── hmdp.sql                         # MySQL 初始化脚本与演示数据
├── pom.xml                          # Maven 依赖与构建配置
└── src
    ├── main
    │   ├── java/com/hmdp
    │   │   ├── config               # MVC、MyBatis-Plus、Redisson 配置
    │   │   ├── controller           # HTTP 接口层
    │   │   ├── dto                  # 请求与响应对象
    │   │   ├── entity               # 数据库实体
    │   │   ├── interceptor          # 登录校验与 Token 刷新
    │   │   ├── mapper               # MyBatis-Plus Mapper
    │   │   ├── service              # 业务接口与实现
    │   │   └── utils                # 缓存、锁、ID、用户上下文等工具
    │   └── resources
    │       ├── application.yaml      # 应用配置
    │       ├── mapper                # MyBatis XML
    │       ├── seckill.lua           # 秒杀资格校验与消息入队
    │       └── unlock.lua            # 分布式锁安全释放脚本
    └── test                          # Redis、Redisson 与登录压测辅助代码
```

## 常见问题

### 启动后持续出现 `NOGROUP` 错误

Redis 中缺少 Stream 消费组。执行：

```bash
redis-cli XGROUP CREATE stream.orders g1 0 MKSTREAM
```

### 店铺详情返回“店铺不存在”

当前店铺详情使用逻辑过期缓存，缓存未命中时不会直接回源数据库。执行 `RedisTest#testSaveShop` 完成预热。

### 附近商铺始终为空

确认请求传入了 `x`、`y` 坐标，并执行 `RedisTest#testLoadShopData` 建立 GEO 索引。

### 图片上传失败

图片目录目前由 `SystemConstants.IMAGE_UPLOAD_DIR` 指定，默认值是开发者本机的 Windows 路径。使用上传功能前，请将它改为本机可写且能被静态资源服务访问的目录。仓库不包含配套前端与 Nginx 静态资源。

### 自动化测试连接失败

测试使用与应用相同的 `application.yaml`，运行前请确保 MySQL、Redis 均已启动且连接配置正确。部分测试用于数据预热或压测，会写入数据库/Redis，并非无副作用的单元测试。

## 学习来源

项目源自黑马程序员的 [Redis 入门到实战教程](https://www.bilibili.com/video/BV1cr4y1671t)，当前仓库在课程案例基础上保留并整理了完整后端实现，适合用于学习 Redis 的典型业务实践。
