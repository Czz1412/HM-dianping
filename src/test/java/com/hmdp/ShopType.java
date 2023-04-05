package com.hmdp;

import com.hmdp.service.impl.ShopTypeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
public class ShopType {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShopTypeServiceImpl service;

    @Test
    void test(){
        List<com.hmdp.entity.ShopType> list = service.query().list();
//        System.out.println(list.toString());
        List<Object> collect = list.stream().map(new Function<com.hmdp.entity.ShopType, Object>() {
            @Override
            public Object apply(com.hmdp.entity.ShopType shopType) {
                String s = shopType.toString();
                return s;
            }
        }).collect(Collectors.toList());

//        stringRedisTemplate.opsForList().leftPushAll("cache:shoptype", );
    }
}
