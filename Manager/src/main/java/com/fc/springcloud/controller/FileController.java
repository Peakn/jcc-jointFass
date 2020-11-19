package com.fc.springcloud.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.fc.springcloud.common.CommonResult;
import com.fc.springcloud.exception.OutOfBusinessException;
import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.domain.FunctionFileDo;
import com.fc.springcloud.pojo.dto.FunctionDto;
import com.fc.springcloud.service.FileService;
import com.fc.springcloud.service.FunctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
public class FileController {

    @Autowired
    private FileService fileService;
    @Autowired
    private FunctionService functionService;

    @GetMapping(value = "/index")
    public CommonResult<List<FunctionFileDo>> index(Model model) {
        // 展示最新二十条数据
        return new CommonResult<>(fileService.listFilesByPage(0, 20));
    }

    /**
     * 分页查询文件
     */
    @GetMapping("files/{pageIndex}/{pageSize}")
    public List<FunctionFileDo> listFilesByPage(@PathVariable int pageIndex, @PathVariable int pageSize) {
        return fileService.listFilesByPage(pageIndex, pageSize);
    }

    /**
     * 获取文件片信息
     */
    @GetMapping("files/{id}")
    public ResponseEntity<Object> serveFile(@PathVariable String id) {
        Optional<FunctionFileDo> file = fileService.getFileById(id);
        if (file.isPresent()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; fileName=" + new String(file.get().getName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1))
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .header(HttpHeaders.CONTENT_LENGTH, file.get().getSize() + "").header("Connection", "close")
                    .body(file.get().getContent());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File was not fount");
    }

    /**
     * 上传接口
     */
    @PostMapping("/upload")
    public ResponseEntity uploadFile(@RequestParam("file") MultipartFile file) {
        FunctionFileDo returnFile = null;
        try {
            FunctionFileDo functionFileDo = new FunctionFileDo();
            functionFileDo.setName(file.getOriginalFilename());
            functionFileDo.setSize(file.getSize());
            functionFileDo.setContentType(file.getContentType());
            functionFileDo.setUploadDate(LocalDateTime.now());
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && originalFilename.contains(".")) {
                String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
                functionFileDo.setSuffix(suffix);
            }
            functionFileDo.setMd5(SecureUtil.md5(file.getInputStream()));
            //将文件存入gridFs
            String gridFsId = fileService.uploadFileToGridFS(file.getInputStream(), file.getContentType());
            functionFileDo.setGridFsId(gridFsId);
            returnFile = fileService.saveFile(functionFileDo);
            return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>(returnFile));

        } catch (IOException ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    /**
     * 创建函数
     *
     * @param functionDto
     * @return
     */
    @PostMapping("/creatFunction")
    public ResponseEntity creatFunction(@RequestBody FunctionDto functionDto){
        return functionService.creatFunction(functionDto);
    }

    /**
     * 删除函数
     *
     * @param functionId
     * @return
     */
    @DeleteMapping("/delete/{functionId}")
    public ResponseEntity deleteFunction(@PathVariable String functionId) {
        if (StrUtil.isEmpty(functionId)) {
            throw new OutOfBusinessException("functionId cannot empty");
        }
        functionService.deleteFunctionByFunctionId(functionId);
        return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>());
    }

    /**
     * 修改函数
     *
     * @param functionDto
     */
    @PutMapping("/updateFunction")
    public ResponseEntity updateFunction(@RequestBody FunctionDto functionDto) {
        functionService.updateFunction(functionDto);
        return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>());
    }

    /**
     * 在线显示文件
     */
    @GetMapping("/functionFile/{functionId}")
    public ResponseEntity<Object> getFunctionFile(@PathVariable String functionId) {
        Optional<FunctionFileDo> file = fileService.getFileById(functionId);
        if (file.isPresent()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + file.get().getName())
                    .header(HttpHeaders.CONTENT_TYPE, file.get().getContentType())
                    .header(HttpHeaders.CONTENT_LENGTH, file.get().getSize() + "").header("Connection", "close")
                    .header(HttpHeaders.CONTENT_LENGTH, file.get().getSize() + "")
                    .body(file.get().getContent());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File was not found");
    }

    /**
     * 删除函数
     *
     * @param functionName
     * @return
     */
    @DeleteMapping("/delete/functionByName")
    public ResponseEntity deleteFunctionByName(String functionName) {
        if (StrUtil.isEmpty(functionName)) {
            throw new OutOfBusinessException("functionName cannot empty");
        }
        FunctionDo function = functionService.getFunction(functionName);
        functionService.deleteFunctionByFunctionName(functionName);
        fileService.removeFile(function.getFunctionId());
        return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>());
    }
}
