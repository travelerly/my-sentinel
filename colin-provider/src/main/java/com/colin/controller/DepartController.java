package com.colin.controller;


import com.colin.bean.Depart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DepartController {

    @GetMapping("/provider/depart/get/{id}")
    public Depart getHandle(@PathVariable("id") int id) {
        int a = 1 / id;
        Depart depart = new Depart();
        depart.setId(id);
        depart.setName("colin-provider");
        return depart;
    }

}
