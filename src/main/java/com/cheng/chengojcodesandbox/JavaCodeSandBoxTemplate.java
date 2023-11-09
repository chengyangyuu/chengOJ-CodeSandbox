package com.cheng.chengojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.cheng.chengojcodesandbox.model.ExecuteCodeRequest;
import com.cheng.chengojcodesandbox.model.ExecuteCodeResponse;
import com.cheng.chengojcodesandbox.model.ExecuteMessage;
import com.cheng.chengojcodesandbox.model.JudgeInfo;
import com.cheng.chengojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * java沙箱代码模板方法的实现
 */
@Slf4j
public abstract class JavaCodeSandBoxTemplate implements CodeSandBox {

    private static final String GLOBAL_CODE_DIR_NAME = "temCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();
        String language = executeCodeRequest.getLanguage();
        //业务流程
//        1.	需要把代码保存在一个文件(防止不同的包导致编译失败)
//        2.	编译这段代码 得到class
//        3.	执行文件 得到输出结果
//        4.	收集 输出结果
//        5.	文件清理
//        6.	错误处理->提升程序健壮性
//1.	把代码保存在一个文件(防止不同的包导致编译失败)
        File userCodeFile = saveCodeToFile(code);

//        2.	编译这段代码 得到class
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        //日志输出
        System.out.println(compileFileExecuteMessage);

//        3.	执行文件 得到输出结果
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile,inputList);

//        4.	收集 输出结果
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);

//5.	文件清理   清理无用文件夹  判断不为空在删 不然如果删空的 会报错
        boolean b = deleteFile(userCodeFile);
        if (!b){
            //清理失败打一个日志
            log.error("deleteFile error,userCodeFilePath={}",userCodeFile.getAbsolutePath());
        }
        return outputResponse;
    }




    /**
     * 1.代码保存为文件
     *
     * @param code
     * @return
     */
    public File saveCodeToFile(String code) {
        String userDir = System.getProperty("user.dir");
        //拼接你的目标仓库 \\为区分不同系统 用File.separator获取
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //hut工具类判断是否存在 不在就新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户每次提交的代码用文件夹隔离一下
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        //把用户的代码写进来  内容,路径
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2.编译用户代码
     *
     * @param userCodeFile 代码文件
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 3.执行文件 获得执行结果列表
     *
     * @param inputList 代码的输入List
     * -Dfile.encoding=UTF-8防中文乱码
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //循环 多组 用例 塞入多组处理信息List
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("执行异常" + e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4.收集 输出结果
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        //遍历执行结果
        List<String> outputList = new ArrayList<>();
        //判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (!StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                //执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            //取最大值 判断超时
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            outputList.add(executeMessage.getMessage());
        }
        //正常运行完成
        //没有错误 每条信息就存入了 状态设置为正常
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
//        judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5.	文件清理   清理无用文件夹
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile){
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除 " + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 6.获取错误响应(定义一个异常封装类)
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //代表沙箱出错
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }







}
