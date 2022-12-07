package com.hmdp.service.impl;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate = null;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //缓存穿透
        //从redis查询商铺id
        String key = keyPrefix + id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(Json)) {
            return JSONUtil.toBean(Json, type);
        }
        //判断命中的是否是空值
        if(Json != null){
            return null;
        }
        //不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        if(r == null){
            //将空值写入redis 防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，写入redis
        this.set(key, r, time, unit);
        return r;
    }

//    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
//                                         Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
//        //缓存击穿
//        //从redis查询商铺id
//        String key = keyPrefix + id;
//        String Json = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(Json)) {
//            return JSONUtil.toBean(Json, type);
//        }
//        //判断命中的是否是空值
//        if(Json != null){
//            return null;
//        }
//        //实现缓存重建
//        //获取互斥锁
//        String lockKey = keyPrefix + id;
//        R r = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            //判断是否获取成功
//            if(!isLock){
//                //失败，休眠并重试
//                Thread.sleep(50);
//                return queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), time, unit);
//            }
//
//            //不存在，根据id查询数据库
//            r = dbFallBack.apply(id);
//            if(r == null){
//                //将空值写入redis 防止缓存穿透
//                stringRedisTemplate.opsForValue().set(key, "", time, unit);
//                return null;
//            }
//            //存在，写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //释放互斥锁
//            unLock(lockKey);
//        }
//
//        return r;
//    }


    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        //逻辑过期
        //从redis查询商铺id
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期
            return r;
        }
        //已经过期需要缓存重建
        String lockKry = keyPrefix + id;
        //获取互斥锁
        boolean isLock = tryLock(lockKry);
        //成功，开启独立线程，实现缓存重建
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //重建缓存
                try {
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKry);
                }
            });
        }

        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
