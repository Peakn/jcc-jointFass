package Manager.function;

import cn.hutool.core.lang.Snowflake;
import com.fc.springcloud.JointFaasApplicationMain;
import com.fc.springcloud.controller.FileController;
import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.dto.CodeBase64;
import com.fc.springcloud.pojo.dto.FunctionDto;
import com.fc.springcloud.provider.Impl.hcloudprovider.HCloudProvider;
import com.fc.springcloud.service.FunctionService;
import com.fc.springcloud.service.ManagerService;
import com.fc.springcloud.util.FileBase64Util;
import org.junit.Assert;
import org.junit.jupiter.api.*;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = JointFaasApplicationMain.class)
class FunctionTest {

    @Autowired
    private Snowflake snowflake;

    @Autowired
    private FileController fileController;

    @Autowired
    private ManagerService managerService;

    @Autowired
    private HCloudProvider hCloudProvider;

    @Autowired
    private FunctionService functionService;

    private static Map<String, String> stringMap = new HashMap<>(4);

    @Test
    @Order(1)
    void createFunction() {
        String zipFile = FileBase64Util.encryptToBase64("C:\\Users\\cossj\\Desktop\\temp\\index.zip");
        FunctionDto functionDto = new FunctionDto();
        functionDto.setFunctionName("test_python_" + snowflake.nextId());
        functionDto.setHandler("com.jcc.springCloud.index");
        functionDto.setInstanceConcurrency(1);
        functionDto.setInstanceType("弹性");
        functionDto.setMemorySize(128);
        functionDto.setRegionId("1777");
        functionDto.setRunEnv("python3");
        functionDto.setServiceName("python_test");
        functionDto.setTimeout(60);
        CodeBase64 codeBase64 = new CodeBase64();
        codeBase64.setZipFile(zipFile);
        functionDto.setCode(codeBase64);
        ResponseEntity responseEntity = fileController.creatFunction(functionDto);
        stringMap.put("functionName", functionDto.getFunctionName());
        System.out.println(responseEntity);
        Assertions.assertEquals(200, responseEntity.getStatusCode().value());
    }

    @Test
    @Order(2)
    void invokeFunctionTest() {
        LocalDateTime date = LocalDateTime.now();
        String result = managerService.InvokeFunction(stringMap.get("functionName"), "{\"a\": \"" + date + "\"}");
        System.out.println("invokeFunctionTest::=>" + result);
        Assertions.assertEquals("{\n" +
                "    \"a\": \"" + date + "\"\n" +
                "}", result);
    }

    @Test
    @Order(3)
    void invokeHCloudFunctionTest() {
        LocalDateTime date = LocalDateTime.now();
        String result = hCloudProvider.InvokeFunction(stringMap.get("functionName"), "{\"a\": \"" + date + "\"}");
        System.out.println(result);
        Assert.assertEquals("{\"a\": \"" + date + "\"}", result);
    }

    @Test
    @Order(4)
    void updateFunctionTest() {
        String zipFile = FileBase64Util.encryptToBase64("C:\\Users\\cossj\\Desktop\\temp\\index.zip");
        FunctionDto functionDto = new FunctionDto();
        functionDto.setFunctionName(stringMap.get("functionName"));
        functionDto.setHandler("com.jcc.springCloud.index_update");
        functionDto.setInstanceConcurrency(1);
        functionDto.setInstanceType("update_instance_type");
        functionDto.setMemorySize(256);
        functionDto.setRegionId("888");
        functionDto.setRunEnv("python3");
        functionDto.setServiceName("python_test");
        functionDto.setTimeout(60);
        CodeBase64 codeBase64 = new CodeBase64();
        codeBase64.setZipFile(zipFile);
        functionDto.setCode(codeBase64);
        ResponseEntity responseEntity = fileController.updateFunction(functionDto);
        System.out.println(responseEntity);
        FunctionDo function = functionService.getFunction(stringMap.get("functionName"));
        Assertions.assertEquals("update_instance_type", function.getInstanceType());
    }


    @Test
    @Order(5)
    void deleteFunctionTest() {
        managerService.DeleteFunction(stringMap.get("functionName"));
        fileController.deleteFunctionByName(stringMap.get("functionName"));
        FunctionDo function = functionService.getFunction(stringMap.get("functionName"));
        Assertions.assertNull(function);
    }
}
