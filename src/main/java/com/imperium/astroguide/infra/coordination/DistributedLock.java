package com.imperium.astroguide.infra.coordination;

/**
 * 跨实例互斥锁：Summary 生成、概念卡写入等场景防重复执行。
 */
public interface DistributedLock {

    /**
     * @return true 若成功获取锁
     */
    boolean tryLock(String lockKey, long ttlMs);

    void unlock(String lockKey);
}
