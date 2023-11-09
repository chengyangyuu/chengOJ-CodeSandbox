package com.cheng.chengojcodesandbox.controller;

import io.netty.channel.unix.Unix;
import org.apache.hc.core5.annotation.Internal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/")
public class MainController {
    @GetMapping("/health")
    public String heathCheck (){
        return "ok";
    }
    //192.168.225.128123456

}
