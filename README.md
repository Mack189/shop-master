# shop-master

#### 项目介绍

shop-master是一款商铺点评类APP，基于SpringBoot 生态开发的实践项目，是一个涉及SpringBoot，MySQL，MybatisPlus，Redis，Nginx等技术栈的项目

#### 项目功能

- 使用 Redis 的 Hash 结构保存用户信息，解决登录用户 session 共享的问题 

- 封装了一个 Redis 工具类，解决了缓存穿透(缓存空对象)、缓存击穿(逻辑过期+互斥锁)和缓存雪崩(给不同的Key设置TTL)问题 

- 基于 Redis 实现了优惠券秒杀功能、并使用 Redis+Lua 脚本来完成秒杀资格判断 

- 基于 Redis 的 SortedSet 数据机构实现推模式 Feed 流，以及点赞排行榜功能 

- 使用 Redis 的 Geo 和 BitMap 数据结构实现查询附近商铺和用户签到功能