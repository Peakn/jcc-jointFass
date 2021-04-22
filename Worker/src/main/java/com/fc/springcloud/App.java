package com.fc.springcloud;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.fc.springcloud.docker.Client;
import com.fc.springcloud.docker.ContainerMaintainer;
import com.fc.springcloud.docker.WorkerServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Worker-java entry
 */
public class App {

  public static void main(String[] args)
      throws IOException, InterruptedException, DockerCertificateException {

    Properties prop = new Properties();
    String fileName = System.getenv("CONFIG_FILE_PATH");
    if (fileName == null) {
      fileName = "./config.properties";
    }
    InputStream is = null;
    try {
      is = new FileInputStream(fileName);
    } catch (FileNotFoundException ex) {
      System.err.println(ex.getMessage());
      System.exit(-1);
    }
    try {
      prop.load(is);
    } catch (IOException ex) {
      System.err.println(ex.getMessage());
      System.exit(-1);
    }
    // just support linux base
    Client client = new Client(DefaultDockerClient.fromEnv().build(), prop);
    ManagedChannel channel = ManagedChannelBuilder
        .forTarget(prop.getProperty("manager", "127.0.0.1:8080")).usePlaintext()
        .build();
    ManagedChannel managedChannel = ManagedChannelBuilder
        .forTarget(prop.getProperty("manager", "127.0.0.1:8080")).usePlaintext()
        .build();
    ContainerMaintainer containerMaintainer = new ContainerMaintainer(client, managedChannel, prop.getProperty("id", "1"));
    WorkerServer workerServer = new WorkerServer(containerMaintainer, prop);
    workerServer.Start(channel);
    workerServer.blockUntilShutdown();

  }
}
