package com.cheng.chengojcodesandbox.model;

import lombok.Data;

/**
 * 封装进程执行返回的信息
 */
@Data
public class ExecuteMessage {
    /**
     * 错误码  不要设int 因为int默认值是0 有可能被认为是错误退出
     */
    private Integer exitValue;

    private String message;

    private String errorMessage;

    //执行用时
    private Long time;
    //执行内存
    private Long memory;

}
