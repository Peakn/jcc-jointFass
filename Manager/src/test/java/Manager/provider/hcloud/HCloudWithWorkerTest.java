package Manager.provider.hcloud;


import com.fc.springcloud.provider.Impl.hcloudprovider.HCloudProvider;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.InvokeException;
import com.fc.springcloud.provider.Impl.hcloudprovider.exception.RuntimeEnvironmentException;
import java.io.IOException;
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
    logger.info("start sleep");
    Thread.sleep(10000); // wait for manager server start.
  }

  @Test
  public void testCreateFunction() {
    // handler is deprecated parameter
    this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime);
  }

  @Test
  public void testCreateAndInvokeFunction() throws InterruptedException {
    // handler is deprecated parameter
    this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime);
    String outputString = null;
    boolean catchError = false;
    try {
      outputString = this.hCloudProvider.InvokeFunction(testFuncName, "{\"a\": \"b\"}");
    } catch (InvokeException e) {
      catchError = true;
    }
    Assert.assertTrue(catchError);
    Thread.sleep(10000);
    outputString = this.hCloudProvider.InvokeFunction(testFuncName, "{\"a\": \"b\"}");
    Assert.assertNotNull(outputString);
    logger.info(new String(outputString));
  }

  @Test
  public void testCreateErrorRuntimeFunction() {
    // handler is deprecated parameter
    try {
      this.hCloudProvider
          .CreateFunction(testFuncName, testCodeURI, testRuntime + "1");
    } catch (RuntimeEnvironmentException e) {
      Assert.assertEquals(1, 1);
      return;
    }
    Assert.assertEquals(1, 0);
  }

  @Test
  public void testDeleteFunction() {
    this.hCloudProvider.CreateFunction(testFuncName, testCodeURI, testRuntime + "1");
    this.hCloudProvider.DeleteFunction(testFuncName);
  }


  @After
  public void tearDown() throws InterruptedException {
    hCloudProvider.stop();
  }
}
