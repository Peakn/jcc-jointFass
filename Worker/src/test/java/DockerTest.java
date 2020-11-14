import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class DockerTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        //仓库地址
        String repository = "peaken/runtime-python3";
        //镜像文件流
//        InputStream imageStream = new FileInputStream("/Users/chenpeng/Desktop/demo/python-docker-app/test.tar");
//        System.out.println(imageStream);
        // 创建镜像

//        File file = new File("/Users/chenpeng/Desktop/demo/python-docker-app/Dockerfile");
//        String imageId = dockerClient.buildImageCmd(file)
//                .exec(new BuildImageResultCallback()).awaitImageId();


        // 创建容器
        CreateContainerResponse container = dockerClient.createContainerCmd("a00")
                .withName("peaken-python")
                .exec();
        System.out.println("成功创建Container.");


        //启动容器
        Object result = dockerClient.startContainerCmd(container.getId()).exec();
        System.out.println("******************************");
        System.out.println(result);
        System.out.println("启动容器成功。");

        Thread.sleep(10000);
        dockerClient.stopContainerCmd(container.getId()).exec();
        System.out.println("停止容器运行");

        Thread.sleep(1000);
        dockerClient.removeContainerCmd(container.getId()).exec();
        System.out.println("删除成功");

        //进入容器执行命令
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getId())
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withCmd("bash", "-c", "ls")
                .exec();

        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(
                new ExecStartResultCallback(System.out, System.err)
        );
    }

}
