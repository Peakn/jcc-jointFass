package com.jcc.springcloud.Docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.BuildImageResultCallback;

import java.io.File;

// docker build -t imageName .
public class CreateImage {
    private static final String DOCKERFILE_PATH = "/Users/chenpeng/Desktop/demo/python-docker-app/src/proto/Dockerfile";


    public static void main(String[] args) {
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();
        File file = new File(DOCKERFILE_PATH);

        BuildImageResultCallback exec = dockerClient.buildImageCmd(file).exec(new BuildImageResultCallback());
        System.out.println(exec.awaitImageId());
    }
}
