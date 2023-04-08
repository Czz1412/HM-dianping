package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 分页查询
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            getUserInfo(blog);
            isBlogLike(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(String id) {
        // 1.根据id查blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }

        // 2.查询用户信息
        getUserInfo(blog);

        // 3.查询blog是否被点赞
        isBlogLike(blog);
        return Result.ok(blog);
    }

    // 判断当前用户是否点赞并设置到blog中
    private void isBlogLike(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户是否已经点赞
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    // 查询博客用户信息,并设置到blog中
    private void getUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 点赞
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        String key = BLOG_LIKED_KEY + id;

        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.判断当前登录用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null){
            // 3.如果未点赞，可以点赞
            // 3.1数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2保存用户到Redis的scoreset集合
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1数据库点赞数 - 1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2把用户的Redis的set集合移除
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }



    /**
     * 根据用户查询
     * @param current
     * @return
     */
    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询点赞排行
     * @param id
     * @return
     */
    @Override
    public Result likeTop(Long id) {
        String key = BLOG_LIKED_KEY + id;

        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        // 2.解析出其中的用户id
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> users = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", users);

        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<User> userList = userService.query().in("id", users).last("ORDER BY FIELD (id," + join + ")").list();
        List<UserDTO> userDTOS = userList.stream().map(user ->
                BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }
}
