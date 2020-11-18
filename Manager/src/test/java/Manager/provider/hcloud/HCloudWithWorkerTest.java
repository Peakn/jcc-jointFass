package Manager.provider.hcloud;


import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

import com.fc.springcloud.provider.Impl.hcloudprovider.HCloudProvider;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.CreateException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.RuntimeEnvironmentException;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import jointfaas.manager.ManagerGrpc;
import jointfaas.manager.ManagerGrpc.ManagerBlockingStub;
import jointfaas.manager.ManagerOuterClass;
import jointfaas.manager.ManagerOuterClass.RegisterResponse.Code;
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
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HCloudWithWorkerTest {

    private static final Log logger = LogFactory.getLog(HCloudTest.class);
    HCloudProvider hCloudProvider;

    String testFuncName = "test-func";
    String testCodeURI = "http://106.15.225.249:8080/index.zip";
    String testRuntime = "python3";

    @Before
    public void setUp() throws IOException, InterruptedException {
        hCloudProvider = new HCloudProvider();
        Thread.sleep(10000); // wait for manager server start.
    }

    @Test
    public void testCreateFunction() {
        // handler is deprecated parameter
        Object result = this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime, "");
        Assert.assertNull(result);
        try {
            this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime, "");
        } catch (CreateException e) {
            Assert.assertEquals(1, 1);
            return;
        }
        Assert.assertEquals(1, 0);
    }

    @Test
    public void testCreateAndInvokeFunction() {
        // handler is deprecated parameter
        Object result = this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime, "");
        Assert.assertNull(result);
        try {
            this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime, "");
        } catch (CreateException e) {
            Assert.assertEquals(1, 1);
            return;
        }
        Assert.assertEquals(1, 0);

        byte[] outputByte = (byte[]) this.hCloudProvider.InvokeFunction(testFuncName, "{}");
        logger.info(new String(outputByte));
    }

    @Test
    public void testCreateErrorRuntimeFunction() {
        // handler is deprecated parameter
        try {
            Object result = this.hCloudProvider
                    .CreateFunction(testFuncName, testCodeURI, testRuntime + "1", "");
        } catch (RuntimeEnvironmentException e) {
            Assert.assertEquals(1, 1);
            return;
        }
        Assert.assertEquals(1, 0);
    }

    @Test
    public void testDeleteFunction() {
        this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime + "1", "");
        this.hCloudProvider.DeleteFunction(testFuncName);
    }


    @After
    public void tearDown() throws InterruptedException {
        hCloudProvider.stop();
    }
}
