package com.fc.springcloud.controller;

import com.aliyuncs.fc.response.ListFunctionsResponse;
import com.fc.springcloud.service.Impl.ManagerServiceImpl;
import com.fc.springcloud.service.ManagerService;
import com.google.common.io.Files;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/manager")
public class mangerController {
    private static final String ROOT_FILE = "/Users/chenpeng/Desktop/demo/code";

    private static final Log logger = LogFactory.getLog(mangerController.class);

    @Autowired
    private ManagerService managerService;

    @RequestMapping(value = "/createFunction")
    public String CreateFunction(@RequestParam String functionName,
                                 @RequestParam String runEnv,
                                 @RequestParam String handler,
                                 @RequestParam MultipartFile file) throws IOException{
//        logger.info(functionName);
//        logger.info(runEnv);
//        logger.info(handler);
        String filePath = uploadFile(file);
        logger.info(filePath);
        if(filePath != null)
            return managerService.CreateFunction(functionName, filePath, runEnv, "jointFass.handler");
        return "Create Function Fail. File Upload Fail.";
    }

    @RequestMapping(value = "/invokeFunction")
    public String InvokeFunction(@RequestParam String functionName){
        return managerService.InvokeFunction(functionName);
    }

    @RequestMapping(value = "/deleteFunction")
    public String DeleteFunction(@RequestParam String functionName){
        return managerService.DeleteFunction(functionName);
    }

    @RequestMapping(value = "/updateFunction")
    public String UpdateFunction(@RequestParam String functionName,
                                 @RequestParam String runEnv,
                                 @RequestParam String handler,
                                 @RequestParam MultipartFile file) throws IOException{
        String filePath = uploadFile(file);
        if(filePath != null)
            return managerService.UpdateFunction(functionName, filePath, runEnv, "jointFass.handler");
        return "Update Function Fail. File Upload Fail.";
    }

    @RequestMapping(value = "/listFunction")
    public ListFunctionsResponse ListFunction(){
        return managerService.ListFunction();
    }

    public String uploadFile(MultipartFile file) throws IOException {
        if(file.isEmpty()){
            logger.info("Empty File.");
            return null;
        }
        String fileName = file.getOriginalFilename();
        logger.info(fileName);

        // 获取当前时间的String形式
        String time = String.valueOf(System.currentTimeMillis());
        String filePath = ROOT_FILE + "/" + time + "/";
        if(!new File(filePath).mkdirs()){
            logger.info("Dictory Create Fail.");
            return null;
        }
        logger.info("文件夹创建成功" + filePath);

        // 复制jointFass到相应的文件目录，作用为统一成aliyun平台的格式
        Files.copy(new File("/Users/chenpeng/Desktop/demo/code/jointFass.py"),
                new File(filePath + "jointFass.py"));
        logger.info("复制JoiintFass到对应文件夹");

        File dest = new File(filePath + fileName);
        logger.info(dest.getPath());
        try{
            file.transferTo(dest);
            logger.info("File Upload Success.");
        } catch (IOException e) {
            logger.info("File Upload Fail.");
            e.printStackTrace();
            return null;
        }
        return filePath;
    }
}
