package com.fc.springcloud.service;


import com.fc.springcloud.pojo.domain.FunctionFileDo;
import com.fc.springcloud.pojo.vo.FunctionFileVo;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface FileService {

    FunctionFileDo saveFile(FunctionFileDo file);

    String uploadFileToGridFS(InputStream in, String contentType);

    void removeFile(String id);

    Optional<FunctionFileDo> getFileById(String id);

    List<FunctionFileDo> listFilesByPage(int pageIndex, int pageSize);

    void updateFileById(FunctionFileVo functionFileVo);
}
