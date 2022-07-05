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

    @SentinelResource(value = "getDepartById",fallback = "getHandlerFallback")
    @GetMapping("/consumer/depart/get/{id}")
    public Depart getHandle(@PathVariable("id") int id) {
        String url = "http://colin-provider/provider/depart/get/" + id;
        int i = 1 / id;
        return restTemplate.getForObject(url, Depart.class);
    }

    /**
     * 处理 blockHandler 的方法，即处理限流、熔断降级
     * @param id
     * @return
     */
    public Depart getHandlerFallback(int id) {
        Depart depart = new Depart();
        depart.setId(id);
        depart.setName("资源被限流降级了-" + id);
        return depart;
    }

}
