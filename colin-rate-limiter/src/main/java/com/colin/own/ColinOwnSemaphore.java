package com.colin.own;

import java.util.concurrent.Semaphore;

/**
 * @author colin
 * @create 2022-10-04 22:54
 *
 * 使用信号量实现的自定义的(线程数)限流器
 * 1.Semaphore 信号量作为一种流控手段，可以对特定资源的允许同时访问的操作数量进行控制，例如池化技术(连接池)中的并发数，有界阻塞容器的容量等；
 * 2.Semaphore 中包含初始化时固定个数的许可，在进行操作的时候，需要先调用 acquire() 方法获取到许可，才可以继续执行任务，
 *   如果获取失败，则进入阻塞；处理完成之后需要调用 release() 方法释放许可；
 * 3.acquire() 与 release() 之间的关系：
 *   在实现中不包含真正的许可对象，并且 Semaphore 也不会将许可与线程关联起来，因此在一个线程中获得的许可可以在另一个线程中释放。
 *   可以将 acquire() 操作视为是消费一个许可，而 release() 操作是创建一个许可，
 *   Semaphore 并不受限于它在创建时的初始许可数量。也就是说 acquire() 与 release() 并没有强制的一对一关系，
 *   release() 一次就相当于新增一个许可，许可的数量可能会由于没有与 acquire() 操作一对一而导致超出初始化时设置的许可个数 ？？？？？？
 */
public class ColinOwnSemaphore {

    private Semaphore semaphore;

    // 构造初始化指定数量的许可证的信号量对象
    public ColinOwnSemaphore(int permits) {
        this.semaphore = new Semaphore(permits);
    }

    // 信号量消费一个许可证
    public boolean tryAcquire() {
        return this.semaphore.tryAcquire();
    }

    // 信号量释放一个许可证(创建一个许可)
    public void release() {
        this.semaphore.release();
    }
}
