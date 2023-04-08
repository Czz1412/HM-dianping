package com.hmdp.service;

public interface ISimpleRedisLock {

    boolean tryLock(Long timeOutSec);

    void unlock();
}
