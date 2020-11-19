package Manager.function;

import cn.hutool.core.lang.Snowflake;
import com.fc.springcloud.JointFaasApplicationMain;
import com.fc.springcloud.controller.FileController;
import com.fc.springcloud.enums.RunEnvEnum;
import com.fc.springcloud.pojo.dto.CodeBase64;
import com.fc.springcloud.pojo.dto.FunctionDto;
import com.fc.springcloud.provider.Impl.hcloudprovider.HCloudProvider;
import com.fc.springcloud.service.ManagerService;
import com.fc.springcloud.util.FileBase64Util;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : zhangjie
 * @date : 2019/3/21
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = JointFaasApplicationMain.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FunctionTest {
    @Autowired
    private Snowflake snowflake;

    @Autowired
    private FileController fileController;

    @Autowired
    private ManagerService managerService;

    @Autowired
    private HCloudProvider hCloudProvider;

    private static Map<String, String> stringMap = new HashMap<>(4);

    @Test
    @Order(1)
    public void createFunction() throws Exception {
        String zipFile = FileBase64Util.encryptToBase64("C:\\Users\\cossj\\Desktop\\temp\\index.zip");
        FunctionDto functionDto = new FunctionDto();
        functionDto.setFunctionName("test_python_" + snowflake.nextId());
        functionDto.setHandler("com.jcc.springCloud.index");
        functionDto.setInstanceConcurrency(1);
        functionDto.setInstanceType("弹性");
        functionDto.setMemorySize(128);
        functionDto.setRegionId("1777");
        functionDto.setRunEnv(RunEnvEnum.python3);
        functionDto.setServiceName("python_test");
        functionDto.setTimeout(60);
        CodeBase64 codeBase64 = new CodeBase64();
        codeBase64.setZipFile(zipFile);
        functionDto.setCode(codeBase64);
        ResponseEntity responseEntity = fileController.creatFunction(functionDto);
        stringMap.put("functionName", functionDto.getFunctionName());
        System.out.println(responseEntity);
    }

    @Test
    @Order(2)
    public void invokeFunctionTest() {
        String functionName = managerService.InvokeFunction("test_python_1329309734238294016", "{\"a\": \"" + LocalDateTime.now() + "\"}");
        System.out.println(functionName);
    }

    @Test
    @Order(3)
    public void invokeHCloudFunctionTest() {
        String functionName = managerService.InvokeFunction(stringMap.get("functionName"), "{\"a\": \"" + LocalDateTime.now() + "\"}");
//        String functionName = hCloudProvider.InvokeFunction("test_python_1329309734238294016", "{\"a\": \"" + LocalDateTime.now() + "\"}");
        System.out.println(functionName);
    }

    @Test
    @Order(4)
    public void updateFunctionTest() {
        String zipFile = FileBase64Util.encryptToBase64("C:\\Users\\cossj\\Desktop\\temp\\index.zip");
        FunctionDto functionDto = new FunctionDto();
        functionDto.setFunctionName(stringMap.get("functionName"));
        functionDto.setHandler("com.jcc.springCloud.index_update");
        functionDto.setInstanceConcurrency(1);
        functionDto.setInstanceType("弹性_update1");
        functionDto.setMemorySize(256);
        functionDto.setRegionId("888");
        functionDto.setRunEnv(RunEnvEnum.python3);
        functionDto.setServiceName("python_test");
        functionDto.setTimeout(60);
        CodeBase64 codeBase64 = new CodeBase64();
        codeBase64.setZipFile(zipFile);
        functionDto.setCode(codeBase64);
        ResponseEntity responseEntity = fileController.updateFunction(functionDto);
        System.out.println(responseEntity);
    }


    @Test
    @Order(5)
    public void deleteFunctionTest() {
        managerService.DeleteFunction(stringMap.get("functionName"));
        fileController.deleteFunctionByName(stringMap.get("functionName"));
    }
}
