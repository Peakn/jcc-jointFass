package com.fc.springcloud.service.Impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.fc.springcloud.common.CommonResult;
import com.fc.springcloud.exception.OutOfBusinessException;
import com.fc.springcloud.mapping.FunctionMapper;
import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.domain.FunctionFileDo;
import com.fc.springcloud.pojo.dto.FunctionDto;
import com.fc.springcloud.pojo.query.FunctionQuery;
import com.fc.springcloud.service.FileService;
import com.fc.springcloud.service.FunctionService;
import com.fc.springcloud.service.ManagerService;
import com.fc.springcloud.util.FileBase64Util;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FunctionServiceImpl implements FunctionService {
    @Autowired
    private FunctionMapper functionMapper;
    @Autowired
    private FileService fileService;
    @Autowired
    private ManagerService managerService;
    @Autowired
    private FunctionService functionService;
    @Autowired
    private Snowflake snowflake;
    @Value("${File.base-dir}")

    private String baseDir;
    @Value("${server.address}")
    private String serverAddress;

    @Value("${server.port}")
    private String serverPort;

    @Override
    public int saveFunction(FunctionDo functionDo) {
        functionDo.setCreatedTime(LocalDateTime.now());
        return functionMapper.insertSelective(functionDo);
    }

    @Override
    public int uploadFunction(FunctionDo functionDo) {
        if (functionDo.getId() == null) {
            throw new OutOfBusinessException("id不能为空");
        }
        functionDo.setLastUpdateTime(LocalDateTime.now());
        return functionMapper.updateByPrimaryKeySelective(functionDo);
    }

    @Override
    public void deleteFunction(Long id) {
        functionMapper.deleteByPrimaryKey(id);
    }

    @Override
    public FunctionDo getFunction(Long id) {
        return functionMapper.selectByPrimaryKey(id);
    }

    @Override
    public Page<FunctionDo> listFunctionByPages(FunctionQuery functionQuery, Pageable page) {
        if (page.getPageNumber() <= 0) {
            throw new OutOfBusinessException("页数不能小于零!");
        }
        PageHelper.startPage(page.getPageNumber(), page.getPageSize());
        List<FunctionDo> list = functionMapper.listFunctionByPages(functionQuery);
        PageInfo<FunctionDo> functionDoPageInfo = new PageInfo<>(list);
        PageImpl<FunctionDo> res = new PageImpl<>(list, PageRequest.of(page.getPageNumber() - 1, page.getPageSize()), functionDoPageInfo.getTotal());
        return res;
    }

    @Override
    public FunctionDo getFunction(String functionName) {
        return functionMapper.selectByFunctionName(functionName);
    }

    @Override
    public int deleteFunctionByFunctionName(String functionName) {
        return functionMapper.deleteFunctionByFunctionName(functionName);
    }

    @Override
    public ResponseEntity updateFunction(FunctionDto functionDto) {
        FunctionDo functionDo = functionMapper.selectByFunctionName(functionDto.getFunctionName());
        if (functionDo == null) {
            throw new OutOfBusinessException("function not exist.");
        }
        fileService.removeFile(functionDo.getFunctionId());
        //将Base64编码的字符串转成文件
        String filePath = baseDir + functionDto.getFunctionName() + "_code.zip";
        Boolean aBoolean = FileBase64Util.decryptByBase64(functionDto.getCode().getZipFile(), filePath);
        if (!aBoolean) {
            throw new OutOfBusinessException("文件转换失败.");
        }
        File file = new File(filePath);
        String contentType = "application/zip";
        FunctionFileDo returnFile;
        try {
            String gridFsId = fileService.uploadFileToGridFS(new FileInputStream(file), contentType);
            FunctionFileDo functionFileDo = new FunctionFileDo();
            functionFileDo.setName(file.getName());
            functionFileDo.setSize(file.length());
            functionFileDo.setContentType(contentType);
            functionFileDo.setUploadDate(LocalDateTime.now());
            functionFileDo.setFunctionName(functionDto.getFunctionName());
            functionFileDo.setSuffix(".zip");
            functionFileDo.setMd5(SecureUtil.md5(file));
            functionFileDo.setGridFsId(gridFsId);
            functionFileDo.setId(functionDo.getFunctionId());
            returnFile = fileService.saveFile(functionFileDo);
            BeanUtils.copyProperties(functionDto, functionDo);
            functionDo.setCodeSize(file.length());

            //3.修改function的functionName等信息
            functionService.uploadFunction(functionDo);
            String path = "http://" + serverAddress + ":" + serverPort + "/functionFile/" + functionDo.getFunctionId();
            managerService.UpdateFunction(functionDo.getFunctionName(), path, functionDto.getRunEnv().getDisplayName());
            FileUtil.del(filePath);
        } catch (Exception ex) {
            this.deleteFunctionByFunctionId(functionDo.getFunctionId());
            throw new OutOfBusinessException("CreatFunction fail:", ex);
        }
        return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>(returnFile));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFunctionByFunctionId(String functionId) {
        if (StrUtil.isEmpty(functionId)) {
            throw new OutOfBusinessException("functionId cannot empty");
        }
        FunctionDo functionDo = functionMapper.selectByFunctionId(functionId);
        if (functionDo == null) {
            throw new OutOfBusinessException("function不存在");
        }
        functionMapper.deleteFunctionByFunctionId(functionId);
        fileService.removeFile(functionId);
        managerService.DeleteFunction(functionDo.getFunctionName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity creatFunction(@RequestBody FunctionDto functionDto) {
        //将Base64编码的字符串转成文件
        String filePath = baseDir + functionDto.getFunctionName() + "_code.zip";
        Boolean aBoolean = FileBase64Util.decryptByBase64(functionDto.getCode().getZipFile(), filePath);
        if (!aBoolean) {
            throw new OutOfBusinessException("文件转换失败.");
        }
        File file = new File(filePath);
        String contentType = "application/zip";
        FunctionFileDo returnFile = null;
        String functionId = IdUtil.fastUUID();
        try {
            //将文件存入gridFs
            String gridFsId = fileService.uploadFileToGridFS(new FileInputStream(file), contentType);
            FunctionFileDo functionFileDo = new FunctionFileDo();
            functionFileDo.setName(file.getName());
            functionFileDo.setSize(file.length());
            functionFileDo.setContentType(contentType);
            functionFileDo.setUploadDate(LocalDateTime.now());
            functionFileDo.setFunctionName(functionDto.getFunctionName());
            functionFileDo.setSuffix(".zip");
            functionFileDo.setMd5(SecureUtil.md5(file));
            functionFileDo.setGridFsId(gridFsId);
            functionFileDo.setId(functionId);
            returnFile = fileService.saveFile(functionFileDo);
            FunctionDo functionDo = new FunctionDo();
            BeanUtils.copyProperties(functionDto, functionDo);
            functionDo.setId(snowflake.nextId());
            functionDo.setFunctionId(functionId);
            functionDo.setCodeSize(file.length());
            functionService.saveFunction(functionDo);
            String path = "http://" + serverAddress + ":" + serverPort + "/functionFile/" + functionId;
            managerService.CreateFunction(functionDo.getFunctionName(), path, functionDto.getRunEnv().getDisplayName());
            FileUtil.del(filePath);
            return ResponseEntity.status(HttpStatus.OK).body(new CommonResult<>(returnFile));
        } catch (Exception ex) {
            this.deleteFunctionByFunctionId(functionId);
            throw new OutOfBusinessException("CreatFunction fail:", ex);
        }
    }

}
