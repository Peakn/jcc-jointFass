package com.jcc.springcloud.Docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;

public class cretaeContainer {
    public static void main(String[] args) {
        DockerClient dockerClient = DockerClientBuilder.getInstance("tcp://localhost:2375").build();

        CreateContainerResponse container = dockerClient.createContainerCmd("hello-test")
                .withName("new-hello-test")
                .exec();

        System.out.println(container.getId());

        dockerClient.startContainerCmd(container.getId()).exec();
    }
}
