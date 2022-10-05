package com.colin.own;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.SneakyThrows;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author colin
 * @create 2022-10-05 19:25
 *
 * Semaphore 模拟限流器
 */
public class SemaphoreTest {
    public static void main(String[] args) {
        Semaphore semaphore = new Semaphore(5);
        Thread t = new Thread(new SemaphoreWorker(semaphore));
        t.start();
    }
}

class SemaphoreWorker implements Runnable {
    private Semaphore semaphore;

    private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(4, 8, 0,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            new ThreadFactoryBuilder().setNameFormat("my_thread_pool_%d").build(), new ThreadPoolExecutor.DiscardOldestPolicy());

    public SemaphoreWorker(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    @SneakyThrows
    @Override
    public void run() {
        while (true) {
            threadPool.execute(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    try {
                        System.out.println(Thread.currentThread().getName() + "等待资源");
                        applyResource();
                        System.out.println(Thread.currentThread().getName() + "申请到资源，开始执行任务");
                        Thread.sleep(1000);
                    } finally {
                        releaseResource();
                        System.out.println(Thread.currentThread().getName() + "释放资源");
                    }
                }
            });

        }
    }

    private void releaseResource() {
        semaphore.release();
    }

    private void applyResource() throws InterruptedException {
        semaphore.acquire();
    }

}