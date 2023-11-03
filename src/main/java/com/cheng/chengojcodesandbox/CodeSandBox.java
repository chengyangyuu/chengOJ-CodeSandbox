package com.cheng.chengojcodesandbox;


import com.cheng.chengojcodesandbox.model.ExecuteCodeRequest;
import com.cheng.chengojcodesandbox.model.ExecuteCodeResponse;

public interface CodeSandBox {

    /**
     * 执行代码
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);


}
