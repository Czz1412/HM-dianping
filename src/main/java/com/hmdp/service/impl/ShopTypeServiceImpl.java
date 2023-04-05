package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> listAllType() {
        // 返回的集合
        List<ShopType> list = new ArrayList<>();

        // 1.从缓存中查询信息
        List<String> range = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE, 0, -1);

        // 2.存在，直接返回
        if (range != null) {
            // 将集合转换
            for (String s : range) {
                list.add(JSONUtil.toBean(s, ShopType.class));
            }
            return list;
        }
        // 3.不存在，到磁盘中查询
        list = query().orderByAsc("sort").list();
        // 4.将查询结果保存到缓存中
        for (ShopType shopType : list) {
            // 将对象转换为字符串
            String s = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForList().rightPush(CACHE_SHOP_TYPE, s);
        }

        // 5.返回
        return list;
    }
}
