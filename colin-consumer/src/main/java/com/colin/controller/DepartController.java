package com.colin.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.colin.bean.Depart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class DepartController {
    @Autowired
    private RestTemplate restTemplate;

    /**
     * sentinel 异常分为流控异常 BlockException 和业务异常 Throwable
     * 流控异常使用 blockHandler 方法处理
     * 业务异常使用 fallback 处理。
     * @param id
     * @return
     */
    @SentinelResource(value = "getDepartById",
            // 业务异常回调方法
            fallback = "getHandlerFallback",fallbackClass = DepartController.class,
            // 流控异常回调方法
            blockHandler = "getHandlerBlockException",blockHandlerClass = DepartController.class
    )
    @GetMapping("/consumer/depart/get/{id}")
    public Depart getHandle(@PathVariable("id") int id) {
        String url = "http://colin-provider/provider/depart/get/" + id;
        int i = 1 / id;
        return restTemplate.getForObject(url, Depart.class);
    }

    /**
     * 业务异常 Throwable 的回调方法
     * @param id
     * @return
     */
    public Depart getHandlerFallback(int id) {
        Depart depart = new Depart();
        depart.setId(id);
        depart.setName("资源被异常降级了-" + id);
        return depart;
    }

    /**
     * 流控异常 BlockException 的回调方法
     * @param id
     * @return
     */
    public Depart getHandlerBlockException(int id){
        Depart depart = new Depart();
        depart.setId(id);
        depart.setName("资源被限流了-" + id);
        return depart;
    }


}
