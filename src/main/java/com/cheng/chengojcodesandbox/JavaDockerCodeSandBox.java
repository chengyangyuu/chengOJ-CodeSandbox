package com.cheng.chengojcodesandbox;

import cn.hutool.core.date.StopWatch;
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
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandBox extends JavaCodeSandBoxTemplate {

    //  超时时间 5s
    private static final long TIME_OUT = 5000L;
    private static final Boolean FIRST_INIT = true;

    public static void main(String[] args) {
        JavaDockerCodeSandBox javaNativeCodeSandBox = new JavaDockerCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        //写一个测试类 测试一下 code就读取文件夹下的值
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 3.覆写执行代码
     *
     * @param userCodeFile
     * @param inputList    代码的输入List
     */
    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
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
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withSecurityOpts(Arrays.asList("secomp=安全管理配置字符串"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        //配置这个容器的特性
        CreateContainerResponse createContainerResponse = containerCmd
                //开启配置(最大使用内存 最大使用cpu 带代码文件 配置代码文件的路径)
                .withHostConfig(hostConfig)
                .withNetworkDisabled(false)
                .withReadonlyRootfs(true)
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
            //抓执行时间
            StopWatch stopWatch = new StopWatch();
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

            long time = 0;
            //回调超时状态
            final boolean[] timeout = {true};

            String execId = execCreateCmdResponse.getId();
            //为了防止耗时操作 需要回调
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    //进入该回调代表执行完 表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }

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

            //最大内存
            final long[] maxMemory = {0L};

            //获取占用内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
//代码提示的一个统计泛型的时间信息
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            statsCmd.exec(statisticsResultCallback);

            try {
                //执行前后 记录时间
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            //塞入 message
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }


}
