package Manager;


import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

import com.fc.springcloud.provider.Impl.hcloudprovider.HCloudProvider;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;
import jointfaas.manager.ManagerGrpc;
import jointfaas.manager.ManagerGrpc.ManagerBlockingStub;
import jointfaas.manager.ManagerOuterClass;
import jointfaas.worker.InitFunctionRequest;
import jointfaas.worker.InitFunctionResponse;
import jointfaas.worker.InvokeRequest;
import jointfaas.worker.InvokeResponse;
import jointfaas.worker.MetricsRequest;
import jointfaas.worker.MetricsResponse;
import jointfaas.worker.RegisterRequest;
import jointfaas.worker.RegisterResponse;
import jointfaas.worker.ResetRequest;
import jointfaas.worker.ResetResponse;
import jointfaas.worker.WorkerGrpc.WorkerImplBase;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HCloudTest {

  HCloudProvider hCloudProvider;

  ManagedChannel channel;

  WorkerImplBase workerServer = mock(WorkerImplBase.class, delegatesTo(
      new WorkerImplBase() {
        @Override
        public void invoke(InvokeRequest request, StreamObserver<InvokeResponse> responseObserver) {
          super.invoke(request, responseObserver);
        }

        @Override
        public void register(RegisterRequest request,
            StreamObserver<RegisterResponse> responseObserver) {
          super.register(request, responseObserver);
        }

        @Override
        public void reset(ResetRequest request, StreamObserver<ResetResponse> responseObserver) {
          super.reset(request, responseObserver);
        }

        @Override
        public void initFunction(InitFunctionRequest request,
            StreamObserver<InitFunctionResponse> responseObserver) {
          super.initFunction(request, responseObserver);
        }

        @Override
        public void metrics(MetricsRequest request,
            StreamObserver<MetricsResponse> responseObserver) {
          super.metrics(request, responseObserver);
        }
      }
  ));

  @Before
  public void setUp() {
    channel = ManagedChannelBuilder.forTarget("127.0.0.1:7777").build();
    ManagerBlockingStub client = ManagerGrpc.newBlockingStub(channel);
  }

  @Test
  public void test001TestRegister() {
  }

  @After
  public void tearDown() throws InterruptedException {
    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
  }
}
