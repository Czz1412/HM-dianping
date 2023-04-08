package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询并缓存商铺信息
     */
    @Override
    public Result queryShopInfo(Long id) {
        // 缓存穿透
        Shop shop = queryWithPassThough(id);

         // 互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        // 逻辑过期方式解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);

        if (shop == null){
            return Result.fail("该商铺不存在！");
        }

        // 7.返回商铺信息
        return Result.ok(shop);
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 逻辑过期方式解决缓存击穿
    private Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从缓存中查询商铺信息
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2.不存在直接返回
        if (StrUtil.isBlank(s)) {
            return null;
        }

        // 3.存在判断是否过期
        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
        JSONObject o = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(o, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (!LocalDateTime.now().isAfter(expireTime)) {
            // 4.未过期，直接返回
            return shop;
        }
        // 5.过期，尝试获取互斥锁
        boolean isFlag = getLock(LOCK_SHOP_KEY + id);

        // 6.获取成功，开启新的线程
        if (isFlag == true) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                // 释放互斥锁
                 unLock(LOCK_SHOP_KEY + id);
                }
            });
        }

        // 7.返回商铺信息
        return shop;
    }

    // 互斥锁解决缓存击穿
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.从缓存中查询商铺信息
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2.存在直接返回
        if (StrUtil.isNotBlank(s)) {
            // 将查询结果转换为对象
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return shop;
        }

        // 命中判断内容是否为空
        if (s != null){
            // 返回错误信息
            return null;
        }

        // 3.不存在
        // 尝试获取锁
        String lock = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean b = getLock(lock);
            if (!b){
                // 获取锁失败，休眠一段时间，再次尝试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 获取锁成功，
            // 根据id到数据库中查询
            shop = getById(id);
            // 4.判断商铺是否存在
            if (shop == null) {
                // 5.不存在返回错误信息
                // 将空值保存到缓存，防止缓存击穿
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6.存在将信息写入缓存
            // 将对象转换为字符串
            String shopInfo = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key, shopInfo, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lock);
        }

        // 7.返回商铺信息
        return shop;
    }

    // 缓存穿透
    private Shop queryWithPassThough(Long id){
        String key = CACHE_SHOP_KEY + id;
        // 1.从缓存中查询商铺信息
        String s = stringRedisTemplate.opsForValue().get(key);
        // 2.存在直接返回
        if (StrUtil.isNotBlank(s)) {
            // 将查询结果转换为对象
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return shop;
        }

        // 命中判断内容是否为空
        if (s != null){
            // 返回错误信息
            return null;
        }

        // 3.不存在根据id到数据库中查询
        Shop shop = getById(id);
        // 4.判断商铺是否存在
        if (shop == null) {
            // 5.不存在返回错误信息
            // 将空值保存到缓存，防止缓存击穿
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6.存在将信息写入缓存
        // 将对象转换为字符串
        String shopInfo = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key, shopInfo, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7.返回商铺信息
        return shop;
    }

    // 将RedisData存入缓存
    private void saveShop2Redis(Long id, Long expireTime){
        // 查询数据
        Shop shop = getById(id);
        // 转换为redisData
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        // 存入缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    // 获取锁的方法
    private boolean getLock(String key){
        Boolean flag =
                stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    /**
     * 更新数据库
     * @param shop
     * @return
     */
    @Override
    public Result update(Shop shop) {
        // 判断shop id
        if (shop.getId() == null){
            return Result.fail("商铺id不存在");
        }

        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
