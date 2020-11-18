package Manager.provider.alicloud;

import com.fc.springcloud.JointFaasApplicationMain;
import com.fc.springcloud.controller.ManagerController;
import com.fc.springcloud.provider.Impl.AliCloudProvider;
import com.fc.springcloud.util.ZipUtil;
import org.apache.catalina.core.ApplicationContext;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

@ContextConfiguration(classes = ManagerController.class)
@RunWith(SpringRunner.class)
@SpringBootTest(classes= JointFaasApplicationMain.class)public class AliCloudTest {
    @Autowired
    AliCloudProvider aliCloudProvider;

    @Test
    public void CreateFunctionTest() throws IOException {
        aliCloudProvider.CreateFunction("TestFunction", "http://106.15.225.249:8080/index.zip", "python3", "");
    }

    @Test
    public void InvokeFunctionTest(){
        aliCloudProvider.InvokeFunction("TestFunction", "{}");
    }

    @Test
    public void DownloadFunction(){
        String codeURI = "http://106.15.225.249:8080/index.zip";
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
}
