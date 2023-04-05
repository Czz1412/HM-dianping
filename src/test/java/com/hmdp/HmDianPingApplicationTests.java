package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    UserServiceImpl userService;



    @Test
    void testuserservice(){

//        User one = userService.query().eq("nick_name","可爱多").one();
        LambdaQueryChainWrapper<User> wrapper = userService.lambdaQuery();
        User one = wrapper.eq(User::getNickName, "可爱多").one();
        System.out.println(one);
    }
}
