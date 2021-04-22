package Manager.provider.alicloud;

import com.fc.springcloud.JointFaasApplicationMain;
import com.fc.springcloud.controller.ManagerController;
import com.fc.springcloud.provider.Impl.alicloud.AliCloudProvider;
import com.fc.springcloud.util.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ResourceUtils;

@ContextConfiguration(classes = ManagerController.class)
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest(classes= JointFaasApplicationMain.class)public class AliCloudTest {
    @Autowired
    AliCloudProvider aliCloudProvider;

    @Test
    public void CreateFunctionTest() throws IOException {
        aliCloudProvider.CreateFunction("pythonA", "http://mesh:8081/pythonA.zip", "python3");
        aliCloudProvider.CreateFunction("pythonB", "http://mesh:8081/pythonB.zip", "python3");
    }

    @Test
    public void InvokeFunctionTest(){
        aliCloudProvider.InvokeFunction("TestFunction2", "{\"a\": \"b\"}");
    }

    @Test
    public void DownloadFunction(){
        String codeURI = "http://mesh:8081/index.zip";
        String runtime = "python3";
        File[] addFiles = new File[1];
        try {
            File tmpFile = new File("huangemingzi1.zip");
            FileUtils.copyURLToFile(new URL(codeURI), tmpFile);
            if (runtime == "python3")
                //append alicloud specific entrypoint into the zipfile
                addFiles[0] = ResourceUtils.getFile("classpath:static/aliCloud/python3/jointfaas.py");
            ZipUtil.addFilesToExistingZip(tmpFile, addFiles);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Merge is use 725 mill
    // Mergev2 is use 127 mill
    @Test
    public void ZipFunction() {
        String codeURI = "http://mesh:8081/index.zip";
        String envCodeURI = "http://mesh:8081/env-py.zip";
        String runtime = "python3";
        try {
            File tmpFile = new File("huangemingzi1.zip");
            File envFile = new File("env.zip");
            FileUtils.copyURLToFile(new URL(codeURI), tmpFile);
            FileUtils.copyURLToFile(new URL(envCodeURI), envFile);
            System.out.println("start merge");
            long timeStart = System.currentTimeMillis();
            ZipFile result = new ZipFile(ZipUtil.Mergev2(tmpFile, envFile));
            long timeEnd  = System.currentTimeMillis();
            System.out.println(timeEnd - timeStart);
            System.out.println("**final result**");
            Enumeration<? extends ZipEntry> entries = result.entries();
            while(entries.hasMoreElements()) {
                System.out.println(entries.nextElement().getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
