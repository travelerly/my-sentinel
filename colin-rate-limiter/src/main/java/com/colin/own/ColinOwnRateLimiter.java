package com.colin.own;

import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author colin
 * @create 2022-10-04 22:20
 *
 * 使用令牌桶实现的自定义的(QPS)限流器
 */
public class ColinOwnRateLimiter {

    /**
     * 令牌桶对象 LinkedBlockingDeque：默认为无界，初始化时可指定具体容量
     * offer() 方法为向令牌桶中存入令牌，若令牌桶未满，则直接存入；若令牌桶已满，则直接丢弃
     * poll() 方法为从令牌桶中取出令牌，若令牌桶中含有令牌，则直接取出，同时将该令牌从令牌桶中删除；若令牌桶中为令牌，则直接返回 null
     */
    private volatile LinkedBlockingDeque<String> blockingDeque = null;


    public ColinOwnRateLimiter(int permitsPerSecond){
        // 创建一个指定容量的令牌桶
        blockingDeque = new LinkedBlockingDeque<String>(permitsPerSecond);
        // 初始化这个令牌桶
        init(permitsPerSecond);
    }
    public void init(int permitsPerSecond){
        // 初始化令牌桶
        addToken(permitsPerSecond);
        // 3.开启单线程定时器，每隔 1s 生成固定的令牌，保存到令牌桶中
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        // 每隔1s 执行
                        Thread.sleep(1000);
                        addToken(permitsPerSecond);
                    } catch (Exception e) {
                        System.out.println("定时器生成令牌错误");
                    }
                }
            }
        }, "tokenThread").start();
    }

    private void addToken(int permitsPerSecond) {
        /**
         * LinkedBlockingDeque：
         * offer() 方法为向令牌桶中存入令牌，若令牌桶未满，则直接存入；若令牌桶已满，则直接丢弃
         * poll() 方法为从令牌桶中取出令牌，若令牌桶中含有令牌，则直接取出，同时将该令牌从令牌桶中删除；若令牌桶中为令牌，则直接返回 null
         */
        for (int i = 0; i < permitsPerSecond; i++) {
            // 令牌为无逻辑的固定占位符
            blockingDeque.offer("#");
        }
    }

    /**
     * 仿照 goole guava 提供的限流器的 API，是否能获取到令牌的判断方法
     * @return 获取令牌是否成功，true 为获取令牌成功；false 为获取令牌失败
     */
    public boolean tryAcquire() {
        return blockingDeque.poll() == null ? false : true;
    }
}
