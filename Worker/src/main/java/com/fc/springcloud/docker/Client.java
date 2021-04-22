package com.fc.springcloud.docker;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.DockerClient.Signal;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.fc.springcloud.dto.Container;
import com.fc.springcloud.dto.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

// Client
public class Client {

  private final DockerClient cli;
  // this is a stupid fix which is intended to help the docker container
  // connect to the Worker running on the host.
  // the localhost is the host address in docker network(172.xx)
  // in future, is should be replaced with network config
  private Properties config;

  public Client(DockerClient cli, Properties config) {
    this.cli = cli;
    this.config = config;
  }

  public Client(String socket, Properties config) {
    this.cli = new DefaultDockerClient(socket);
    this.config = config;
  }

  private Container ConvertContainer(com.spotify.docker.client.messages.Container c) {
    Resource resource = new Resource();
    try {
      ContainerInfo info = cli.inspectContainer(c.id());
      for (String env : Objects.requireNonNull(info.config().env())) {
        String[] pair = env.split("=");
        if (pair[0].equals("FUNC_NAME")) {
          resource.setFuncName(pair[1]);
          break;
        }
      }
      return new Container(c.id(), info.networkSettings().ipAddress(), resource);
    } catch (DockerException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Container CreateContainer(Resource resource) {
    List<String> env = new ArrayList<>();

    env.add("WORK_HOST=" + this.config.getProperty("worker"));
    env.add("MEMORY=" + resource.memorySize);
    env.add("FUNC_NAME=" + resource.funcName);
    env.add("CODE_URI=" + resource.codeUrI);
    env.add("POLICY="+ config.getProperty("policy", "simple"));
    env.add("MESH=" + config.getProperty("mesh", "106.15.225.249:40041"));
    // runtime will know it's own memory limitation
    env.add("NMSL=1");
    try {
      Map<String, String> labels = new HashMap<>();
      labels.put("type", "jointfaas");
      labels.put("function", resource.getFuncName());
      cli.pull(resource.getImage());
      final ContainerCreation creation = cli.createContainer(ContainerConfig.builder()
          .image(resource.getImage())
          .labels(labels)
          .env(env)
          .hostConfig(HostConfig.builder()
              .memory(resource.getMemorySize() * 1024 * 1024)
              .networkMode("bridge")
              //todo set cpu quota
              .build()
          )
          .build()
      );
      String identity = creation.id();
      String ip = this.cli.inspectContainer(identity).networkSettings().ipAddress();
      this.cli.startContainer(identity);
      return new Container(identity, ip, resource);
    } catch (DockerException | InterruptedException e) {
      e.printStackTrace();
    }
    return null;
  }

  public void DeleteContainer(String identity) {
    try {
        cli.killContainer(identity, Signal.SIGTERM);
      }
    catch (DockerException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void PauseContainer(String identity) {
    try {
      cli.pauseContainer(identity);
    } catch (DockerException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void UnpauseContainer(String identity) {
    try {
      cli.unpauseContainer(identity);
    } catch (DockerException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }


  public List<Container> ListPausedContainers(Resource resource) {
    List<Container> containerList = new ArrayList<>();
    try {
      List<com.spotify.docker.client.messages.Container> containers = cli
          .listContainers(ListContainersParam.withStatusPaused(),
              ListContainersParam.withLabel("type=jointfaas"), ListContainersParam.withLabel("function="+ resource.getFuncName()));
      for(com.spotify.docker.client.messages.Container c : containers) {
        Container container = ConvertContainer(c);
        if (container != null) {
          containerList.add(container);
        }
      }
      return containerList;
    } catch (DockerException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return containerList;
  }

  public List<Container> ListContainerHCloud() {
    List<com.spotify.docker.client.messages.Container> containers = null;
    try {
      containers = cli
          .listContainers(ListContainersParam.withStatusRunning(), ListContainersParam.withLabel("type=jointfaas"));
    } catch (DockerException e) {
      e.printStackTrace();
      return new ArrayList<>();
    } catch (InterruptedException e) {
      e.printStackTrace();
      return new ArrayList<>();
    }
    List<Container> containerList = new ArrayList<>();
    for(com.spotify.docker.client.messages.Container c : containers) {
      Container container = ConvertContainer(c);
      if (container != null) {
        containerList.add(container);
      }
    }
    return containerList;
  }
}
