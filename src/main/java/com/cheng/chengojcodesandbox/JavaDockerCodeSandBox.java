package com.cheng.chengojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.cheng.chengojcodesandbox.model.ExecuteCodeRequest;
import com.cheng.chengojcodesandbox.model.ExecuteCodeResponse;
import com.cheng.chengojcodesandbox.model.ExecuteMessage;
import com.cheng.chengojcodesandbox.model.JudgeInfo;
import com.cheng.chengojcodesandbox.utils.ProcessUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaDockerCodeSandBox implements CodeSandBox {
    //定义两个常量魔法值
    private static final String GLOBAL_CODE_DIR_NAME = "temCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaNativeCodeSandBox = new JavaDockerCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        //写一个测试类 测试一下 code就读取文件夹下的值
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        executeCodeRequest.setLanguage("java");
        javaNativeCodeSandBox.executeCode(executeCodeRequest);
    }

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
        //获取你的目标文件夹是否存在 用huTool工具类
        //获取当前项目目录
        String userDir = System.getProperty("user.dir");
        //拼接你的目标仓库 \\为区分不同系统 用File.separator获取
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //hut工具类判断是否存在 不在就新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //把用户每次提交的代码用文件夹隔离一下 不能全混一起
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        //把用户的代码写进来  内容,路径
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        //编译这个目录下的文件
        //编译目录
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        3.//创建容器 把文件放到容器内
        //3.1先拉取java环境
        //定义一个执行列表
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        //定义镜像名称
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            //拉镜像需要一个回调检测状态
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像" + item.getStatus());
                }
            };
            //执行回调 由于是异步 执行一个阻塞 没下载完就一直下载
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("下载完成");
        }
        //镜像下创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 100 * 1000L);
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        //配置这个容器的特性
        CreateContainerResponse createContainerResponse = containerCmd
                //开启配置(最大使用内存 最大使用cpu 带代码文件 配置代码文件的路径)
                .withHostConfig(hostConfig)
                //开启与终端交互的能力
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withAttachStdin(true)
                .withTty(true)
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();
        //docker exec keen_ java -cp /app Main 1 3
        //容器 执行 带有程序和参数  并获取结果
        for (String inputArgs : inputList) {
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令" + execCreateCmdResponse);
            //程序执行完之后 塞进去
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};

            String execId = execCreateCmdResponse.getId();
            //为了防止耗时操作 需要回调
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        //把错误结果放到错误信息中
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果:" + message[0]);
                    }
                    super.onNext(frame);
                }
            };
            try {
                dockerClient.execStartCmd(execId).exec(execStartResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            //塞入 message
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessageList.add(executeMessage);
        }

        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        return executeCodeResponse;
    }

}
