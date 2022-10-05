package com.colin.own;

import java.util.concurrent.Semaphore;

/**
 * @author colin
 * @create 2022-10-04 22:54
 *
 * 使用信号量实现的自定义的(线程数)限流器
 * Semaphore 是一种计数信号量，利用它可以控制一定数量的请求，从而实现资源访问限制的目的，实际使用中，可以从来限制访问某种资源的请求数量；
 * 比如在 Hystrix 中就是基于 Semaphore 实现线程数隔离限流的。
 * 最简单的理解信号量，就是由一个计数器、一个等待队列和两个方法组成的，Java 实现的 Semaphore 中就是 acquire() 方法和 release() 方法
 * 1.调用一次 acquire() 方法，计数器就减 1，如果此时计数器小于 0，则阻塞当前线程，否则当前线程可继续执行；
 * 2.调用一次 release() 方法，计数器就加 1，如果此时计数器小于等于 0，则唤醒一个等待队列中的线程，并将这个线程从等待队列中移除
 *
 * Java 中的 Semaphore 的 API
 * void acquire()：获取一个信号量许可，在获取到许可之前一直阻塞
 * void release()：释放一个信号量许可
 *
 * 多线程获取信号量流程：
 * 如果在线程 A 获取到信号量许可的执行过程中，有另一个线程 B 来申请获取信号量许可，此时根据计数器是否还有许可剩余，
 * 如果有，那么线程 B 就能够获取到信号量许可
 * 如果没有，那么线程 B 就会被阻塞，进入等待队列，
 * 当线程 A 执行完毕后，会释放信号量许可，那么计数器中便又有了许可可用，就或去唤醒等待队列中的线程 B
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
