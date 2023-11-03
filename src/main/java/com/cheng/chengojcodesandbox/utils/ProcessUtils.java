package com.cheng.chengojcodesandbox.utils;


import cn.hutool.core.util.StrUtil;
import com.cheng.chengojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.io.*;

/**
 * 进程工具类
 */
public class ProcessUtils {

    /**
     * 执行进程并获取信息
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            //获取编译时 的错误码 .waitFor() 看正常退出还是异常退出 判断是否编译成功
            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            //获取执行信息
            if (exitValue == 0) {
                System.out.println(opName + "成功");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                String compileOutput;
                //一行一行向下码 输出信息  用Builder拼接
                while ((compileOutput = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutput);
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());
            } else {
                //异常退出
                System.out.println(opName + "失败，错误码" + exitValue);
                //getError 读错误流
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                String compileOutput;
                //一行一行向下码 输出信息  用Builder拼接
                while ((compileOutput = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutput);
                }
                System.out.println(compileOutputStringBuilder.toString());

                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder compileErrorOutputStringBuilder = new StringBuilder();
                String errorCompileOutput;
                //一行一行向下码 输出信息
                while ((errorCompileOutput = errorBufferedReader.readLine()) != null) {
                    compileErrorOutputStringBuilder.append(errorCompileOutput);
                }
                executeMessage.setMessage(compileErrorOutputStringBuilder.toString());
                stopWatch.stop();
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }

    /**
     * 执行交互式进程并获取信息
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process runProcess, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            //向控制台输出程序 我们要给终端写信息 不然终端会卡住等你的值
            OutputStream outputStream = runProcess.getOutputStream();
            //往输出流写
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            String[] s = args.split(" ");
            String join =StrUtil.join("\n", s)+"\n";
            outputStreamWriter.write(join);
            //相当于按了回车 执行输入的发送
            outputStreamWriter.flush();

            //先分批获取正常输入
            InputStream inputStream = runProcess.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            String compileOutput;
            //一行一行向下码 输出信息  用Builder拼接
            while ((compileOutput = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutput);
            }
            executeMessage.setMessage(compileOutputStringBuilder.toString());
            //交互进程类的程序 执行完 释放进程 否则会卡
            outputStream.close();
            inputStream.close();
            outputStreamWriter.close();
            runProcess.destroy();
        }catch (Exception e){
            e.printStackTrace();
        }
            return executeMessage;
        }

    }
