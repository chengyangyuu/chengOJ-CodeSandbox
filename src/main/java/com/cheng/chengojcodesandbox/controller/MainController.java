package com.cheng.chengojcodesandbox.controller;

import com.cheng.chengojcodesandbox.JavaNativeCodeSandBox;
import com.cheng.chengojcodesandbox.model.ExecuteCodeRequest;
import com.cheng.chengojcodesandbox.model.ExecuteCodeResponse;
import io.netty.channel.unix.Unix;
import org.apache.hc.core5.annotation.Internal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController("/")
public class MainController {

    @Resource
    private JavaNativeCodeSandBox javaNativeCodeSandBox;

    @GetMapping("/health")
    public String heathCheck (){
        return "ok";
    }
    //192.168.225.128123456

    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode (@RequestBody ExecuteCodeRequest executeCodeRequest){
        if (executeCodeRequest==null){
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandBox.executeCode(executeCodeRequest);
    }

}
