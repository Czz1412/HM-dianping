package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 新增秒杀劵订单
     *
     * @param voucherId
     * @return
     */
    @Override

    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠劵信息
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }

        // 3.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("秒杀劵已分发完");
        }

        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 获取锁
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = simpleRedisLock.tryLock(1L);
        // 获取失败
        if (!isLock){
            // 返回失败信息
            return Result.fail("一人只能下一单");
        }


        try {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            simpleRedisLock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOder(Long voucherId) {
        // 4.根据优惠劵id和用户id查询订单

        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        Integer count =
                lambdaQuery().eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getId, voucherId).count();

        // 5.判断订单是否存在
        if (count > 0) {
            // 6.订单存在，返回异常
            return Result.fail("该用户已经购买过了");
        }

        // 6.扣除库存
        boolean success =
                iSeckillVoucherService.update()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", voucherId)
                        .gt("stock", 0)
                        .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2用户id
        voucherOrder.setUserId(userId);
        // 7.3代金券id
        voucherOrder.setVoucherId(voucherId);
        // 将订单存入数据库
        save(voucherOrder);

        // 8.返回订单
        return Result.ok(orderId);

    }
}
