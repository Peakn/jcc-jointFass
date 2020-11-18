package com.fc.springcloud.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.crypto.SecureUtil;
import com.fc.springcloud.common.CommonResult;
import com.fc.springcloud.entity.FunctionFileDocument;
import com.fc.springcloud.service.FileService;
import com.fc.springcloud.service.ManagerService;
import com.fc.springcloud.vo.FunctionFileVo;
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

    @GetMapping(value = "/index")
    public CommonResult<List<FunctionFileDocument>> index(Model model) {
        // 展示最新二十条数据
        return new CommonResult<>(fileService.listFilesByPage(0, 20));
    }

    /**
     * 分页查询文件
     */
    @GetMapping("files/{pageIndex}/{pageSize}")
    public List<FunctionFileDocument> listFilesByPage(@PathVariable int pageIndex, @PathVariable int pageSize) {
        return fileService.listFilesByPage(pageIndex, pageSize);
    }

    /**
     * 获取文件片信息
     */
    @GetMapping("files/{id}")
    public ResponseEntity<Object> serveFile(@PathVariable String id) {
        Optional<FunctionFileDocument> file = fileService.getFileById(id);
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
     * 在线显示文件
     */
    @GetMapping("/view")
    public ResponseEntity<Object> serveFileOnline(@RequestParam("id") String id) {
        Optional<FunctionFileDocument> file = fileService.getFileById(id);
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
     * 上传接口
     */
    @PostMapping("/upload")
    public ResponseEntity handleFileUpload(@RequestParam("file") MultipartFile file) {
        FunctionFileDocument returnFile = null;
        try {
            FunctionFileDocument functionFileDocument = new FunctionFileDocument();
            functionFileDocument.setName(file.getOriginalFilename());
            functionFileDocument.setSize(file.getSize());
            functionFileDocument.setContentType(file.getContentType());
            functionFileDocument.setUploadDate(LocalDateTime.now());
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && originalFilename.contains(".")) {
                String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
                functionFileDocument.setSuffix(suffix);
            }
            functionFileDocument.setMd5(SecureUtil.md5(file.getInputStream()));
            //将文件存入gridFs
            String gridFsId = fileService.uploadFileToGridFS(file.getInputStream(), file.getContentType());
            functionFileDocument.setGridFsId(gridFsId);
            returnFile = fileService.saveFile(functionFileDocument);
            return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>(returnFile));

        } catch (IOException ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }

        //TODO: Upload function code to Aliyun and Hcloud

    }

    /**
     * 删除文件
     */
    @GetMapping("/delete")
    public CommonResult deleteFile(@RequestParam("id") String id) {
        if (!StrUtil.isEmpty(id)) {
            fileService.removeFile(id);
            return new CommonResult("删除成功");
        } else {
            return new CommonResult("请传入文件id");
        }
    }

    /**
     * 修改文件
     * @param functionFileVo
     */
    @PutMapping("/update")
    public ResponseEntity<Object> updateFileById(@RequestBody FunctionFileVo functionFileVo) {
        fileService.updateFileById(functionFileVo);
        return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>());
    }
}
