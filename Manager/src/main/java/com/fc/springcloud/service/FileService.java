package com.fc.springcloud.service;


import com.fc.springcloud.entity.FunctionFileDocument;
import com.fc.springcloud.vo.FunctionFileVo;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface FileService {

    FunctionFileDocument saveFile(FunctionFileDocument file);

    String uploadFileToGridFS(InputStream in, String contentType);

    void removeFile(String id);

    Optional<FunctionFileDocument> getFileById(String id);

    List<FunctionFileDocument> listFilesByPage(int pageInex, int pageSize);

    void updateFileById(FunctionFileVo functionFileVo);
}
