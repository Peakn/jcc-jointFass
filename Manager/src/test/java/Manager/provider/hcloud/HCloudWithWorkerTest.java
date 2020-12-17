package Manager.provider.hcloud;


import com.fc.springcloud.mesh.MeshClient;
import com.fc.springcloud.provider.Impl.hcloud.HCloudProvider;
import com.fc.springcloud.provider.Impl.hcloud.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloud.exception.RuntimeEnvironmentException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

  String testFuncNameA = "test-funcA";
  String testFuncNameB = "test-funcB";
  String testCodeURIA = "http://106.15.225.249:8080/index-old.zip";
  String testCodeURIB = "http://106.15.225.249:8080/index-old-callee.zip";
  String testRuntime = "python3";
  MeshClient meshClient = null;
  String applicationName = "test-hcloud";

  @Before
  public void setUp() throws IOException, InterruptedException {

    hCloudProvider = new HCloudProvider();
    meshClient = new MeshClient();
    meshClient.setDefinition("106.15.225.249:50051");
    meshClient.setTarget("106.15.225.249:40041");
    meshClient.setTracePort(6832);
    meshClient.setTraceHost("106.15.225.249");
    meshClient.deleteFunctionInMesh(testFuncNameA);
    meshClient.createFunctionInMesh(testFuncNameA, "GET");
    hCloudProvider.setMeshInjector(meshClient);
    logger.info("start sleep");
    Thread.sleep(10000); // wait for worker server start.
  }

  @Test
  public void testCreateFunction() {
    // handler is deprecated parameter
    this.hCloudProvider.CreateFunction(testFuncNameA, testCodeURIA, testRuntime);
  }

  @Test
  public void testCreateAndInvokeFunction() throws InterruptedException {
    // handler is deprecated parameter
    this.hCloudProvider.CreateFunction(testFuncNameA, testCodeURIA, testRuntime);
    String outputString = null;
    boolean catchError = false;
    try {
      outputString = this.hCloudProvider.InvokeFunction(testFuncNameA, "{\"a\": \"b\"}");
    } catch (InvokeException e) {
      catchError = true;
    }
    Assert.assertTrue(catchError);
    Thread.sleep(10000);
    outputString = this.hCloudProvider.InvokeFunction(testFuncNameA, "{\"a\": \"b\"}");
    Assert.assertNotNull(outputString);
    logger.info(new String(outputString));
  }

  @Test
  public void testCreateErrorRuntimeFunction() {
    // handler is deprecated parameter
    try {
      this.hCloudProvider
          .CreateFunction(testFuncNameA, testCodeURIA, testRuntime + "1");
    } catch (RuntimeEnvironmentException e) {
      Assert.assertEquals(1, 1);
      return;
    }
    Assert.assertEquals(1, 0);
  }

  @Test
  public void testDeleteFunction() {
    this.hCloudProvider.CreateFunction(testFuncNameA, testCodeURIA, testRuntime + "1");
    this.hCloudProvider.DeleteFunction(testFuncNameA);
  }

  @Test
  public void testCreateApplicationAndInvoke() throws InterruptedException {
    // handler is deprecated parameter
    meshClient.deleteFunctionInMesh(testFuncNameB);
    meshClient.createFunctionInMesh(testFuncNameB, "GET");
    this.hCloudProvider.CreateFunction(testFuncNameA, testCodeURIA, testRuntime);
    this.hCloudProvider.CreateFunction(testFuncNameB, testCodeURIB, testRuntime);
    List<String> steps = new ArrayList<>();
    try {
      meshClient.deleteApplication(applicationName);
    } catch (RuntimeException e) {
      logger.warn(e.getMessage());
    }
    steps.add(testFuncNameA);
    steps.add(testFuncNameB);
    try {
      meshClient.createApplication(applicationName, steps);
    } catch (RuntimeException e) {
      logger.warn(e.getMessage());
      Assert.assertTrue(false);
    }

    String outputString = null;
    boolean catchError = false;
    // attention here is for test, we need to use invoke to create function B.
    // If not, the function A will can not call a specific B instance.
    try {
      outputString = this.hCloudProvider.InvokeFunction(testFuncNameB, "{\"a\": \"b\"}");
    } catch (InvokeException e) {
      catchError = true;
    }
    Assert.assertTrue(catchError);
    catchError = false;
    try {
      outputString = this.hCloudProvider.InvokeFunction(testFuncNameA, "{\"a\": \"b\"}");
    } catch (InvokeException e) {
      catchError = true;
    }
    Assert.assertTrue(catchError);
    Thread.sleep(10000);
    outputString = this.hCloudProvider.InvokeFunction(testFuncNameA, "{\"a\": \"b\"}");
    Assert.assertNotNull(outputString);
    logger.info(new String(outputString));
  }


  @After
  public void tearDown() throws InterruptedException {
    hCloudProvider.stop();
  }
}
