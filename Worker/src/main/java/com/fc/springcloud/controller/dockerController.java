package com.fc.springcloud.controller;

import com.fc.springcloud.service.Impl.WorkServiceImpl;
import com.fc.springcloud.service.WorkService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/docker")
public class dockerController {

    private static final Log logger = LogFactory.getLog(dockerController.class);

    @Autowired
    WorkService workService = new WorkServiceImpl();

    @RequestMapping(value = "/listImages")
    public Object listImages(){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();
        // 获取服务器上的镜像
        List<Image> images = dockerClient.listImagesCmd().exec();
        for(Image image : dockerClient.listImagesCmd().exec()){
            System.out.println(image.getRepoTags()[0]);
        }
        return images;
    }

    @RequestMapping(value = "/exist")
    public String ExistImage(@RequestParam String imageName){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();
        // 获取服务器上的镜像
        List<Image> images = dockerClient.listImagesCmd().withImageNameFilter(imageName).exec();
        if(images.isEmpty()){
            return "没有该镜像";
        }
        else{
            return "有";
        }

    }
    @RequestMapping(value = "/searchImage")
    public Object SearchDocker(@RequestParam String imageName){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        List<SearchItem> dockerSearch = dockerClient.searchImagesCmd(imageName).exec();
        for (SearchItem item : dockerSearch){
            System.out.println(item.getName());
        }
        return dockerSearch;
    }

    @RequestMapping(value = "/buildImage")
    public Object buildImage() throws FileNotFoundException {
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        File file = new File("/Users/chenpeng/Desktop/demo/python-docker-app/Dockerfile");

        String result = dockerClient.buildImageCmd(file).exec(new BuildImageResultCallback()).awaitImageId();
        System.out.println(result);
        return result;
    }

    @RequestMapping(value = "/pullImage")
    public ResultCallback<PullResponseItem> pullImage(@RequestParam String imageName){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        ResultCallback<PullResponseItem> result = dockerClient.pullImageCmd(imageName).exec(new ResultCallback<PullResponseItem>() {
            public void onStart(Closeable closeable) {
                System.out.println("开始下载!");
            }

            public void onNext(PullResponseItem object) {
                System.out.println(object.getStatus());
            }

            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            public void onComplete() {
                System.out.println("下载完毕!");
            }

            public void close() throws IOException {

            }
        });
        return result;
    }

    @RequestMapping(value = "/deleteImage")
    public String deleteImage(@RequestParam String imageName){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        dockerClient.removeImageCmd(imageName).exec();
        System.out.println("删除成功");
        return "删除成功";
    }
    @RequestMapping(value = "/createContainer")
    public String CreateContainer(@RequestParam String containerName){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        //创建容器
        CreateContainerResponse container1 = dockerClient.createContainerCmd("a00")
                .withName(containerName)                //给容器命名
                .exec();
        logger.info("创建容器成功");

        //运行容器
        dockerClient.startContainerCmd(container1.getId()).exec();
        logger.info("运行容器成功");

        //创建命令
        ExecCreateCmdResponse createCmdResponse = dockerClient.execCreateCmd(container1.getId())
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("-bash", "-c", "ls")
                .exec();
        logger.info("创建命令成功");

        //执行命令
        dockerClient.execStartCmd(createCmdResponse.getId()).exec(
                new ExecStartResultCallback(System.out, System.err)
        );
        logger.info("执行命令成功");

        return "成功";
    }

    //获取容器
    @RequestMapping(value = "/listContainer")
    public List<Container> ListContainer(){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        //获取所有运行的容器
        List<Container> containers = dockerClient.listContainersCmd().exec();
        for(Container container : containers){
            logger.info(container.getId() + " : " + container.getNames()[0]);
        }
        System.out.println("正在运行的容器：\n" + containers);

        //获取所有运行结束的容器
        List<Container> exitedContainers = dockerClient.listContainersCmd()
                .withStatusFilter(Collections.singleton("exited")).exec();
        for(Container exitedContainer : exitedContainers){
            logger.info(exitedContainer.getId() + " : " + exitedContainer.getNames()[0]);
        }
        System.out.println("已经退出的容器：\n" + exitedContainers);

        return exitedContainers;
    }

    @RequestMapping(value = "/container")
    public void ControllerContainer(@RequestParam String imageName,
                                    @RequestParam String containerName){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();
        CreateContainerResponse container1 = dockerClient.createContainerCmd(imageName)
                .withName(containerName)
                .exec();

        //开始容器
        dockerClient.startContainerCmd(container1.getId()).exec();
        // 停止容器
        dockerClient.stopContainerCmd(container1.getId()).exec();
        // 重启容器
        dockerClient.restartContainerCmd(container1.getId()).exec();
        // 暂停容器
        dockerClient.pauseContainerCmd(container1.getId()).exec();
        // 恢复容器
        dockerClient.unpauseContainerCmd(container1.getId()).exec();
        // 删除容器
//        dockerClient.removeContainerCmd(container1.getId()).exec();
    }

   @RequestMapping(value = "/hello")
    public String Hello(){
        String result = workService.Test("xiao");
        logger.info(result);
        return result;
    }


    @RequestMapping(value = "/test")
    public Info test(){
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();
        Info info = dockerClient.infoCmd().exec();
        logger.info(info);
        System.out.println(info);
        return info;
    }
}
