package com.fc.springcloud.service.Impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.fc.springcloud.common.Result;
import com.fc.springcloud.enums.ResultCode;
import com.fc.springcloud.enums.RunEnvEnum;
import com.fc.springcloud.exception.EntityExistsException;
import com.fc.springcloud.exception.EntityNotFoundException;
import com.fc.springcloud.exception.OutOfBusinessException;
import com.fc.springcloud.mapping.FunctionMapper;
import com.fc.springcloud.mesh.MeshClient;
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
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class FunctionServiceImpl implements FunctionService {

  private static final Logger logger = LoggerFactory.getLogger(FunctionServiceImpl.class);
  @Autowired
  private FunctionMapper functionMapper;
  @Autowired
  private FileService fileService;
  @Autowired
  private ManagerService managerService;
  @Autowired
  private Snowflake snowflake;
  @Autowired
  private MeshClient meshClient;
  @Value("${File.base-dir}")

  private String baseDir;

  @Value("${server.exportAddress}")
  private String serverAddress;

  @Value("${server.port}")
  private String serverPort;

  @Value("${mesh.use}")
  private boolean enableInject;

  @Override
  public int saveFunction(FunctionDo functionDo) {
    functionDo.setCreatedTime(LocalDateTime.now());
    // todo resolve the problem about method hardcode
    if (enableInject) {
      meshClient.createFunctionInMesh(functionDo.getFunctionName(), "GET");
    }
    return functionMapper.insertSelective(functionDo);
  }

  @Override
  public int uploadFunction(FunctionDo functionDo) {
    if (functionDo.getId() == null) {
      throw new OutOfBusinessException("Function id cannot empty",
          ResultCode.BAD_REQUEST.getCode());
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
      throw new OutOfBusinessException("Page index must not be less than zero !",
          ResultCode.BAD_REQUEST.getCode());
    }
    PageHelper.startPage(page.getPageNumber(), page.getPageSize());
    List<FunctionDo> list = functionMapper.listFunctionByPages(functionQuery);
    PageInfo<FunctionDo> functionDoPageInfo = new PageInfo<>(list);
    return new PageImpl<>(list, PageRequest.of(page.getPageNumber() - 1, page.getPageSize()),
        functionDoPageInfo.getTotal());
  }

  @Override
  public FunctionDo getFunction(String functionName) {
    return functionMapper.selectByFunctionName(functionName);
  }

  @Override
  public int deleteFunctionByFunctionName(String functionName) {
    if (enableInject) {
      meshClient.deleteFunctionInMesh(functionName);
    }
    return functionMapper.deleteFunctionByFunctionName(functionName);
  }

  @Override
  public ResponseEntity updateFunction(FunctionDto functionDto) {
    FunctionDo functionDo = functionMapper.selectByFunctionName(functionDto.getFunctionName());
    if (functionDo == null) {
      throw new EntityNotFoundException("Function not exist.");
    }
    fileService.removeFile(functionDo.getFunctionId());
    //将Base64编码的字符串转成文件
    String filePath = baseDir + functionDto.getFunctionName() + "_code.zip";
    Boolean aBoolean = FileBase64Util.decryptByBase64(functionDto.getCode().getZipFile(), filePath);
    if (!aBoolean) {
      throw new OutOfBusinessException("File conversion failed.", ResultCode.BAD_REQUEST.getCode());
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
      functionDo.setRunEnv(RunEnvEnum.valueOf(functionDto.getRunEnv()));
      //3.修改function的functionName等信息
      this.uploadFunction(functionDo);
      String path = "http://" + serverAddress + ":" + serverPort + "/functionFile/" + functionDo
          .getFunctionId();
      managerService.UpdateFunction(functionDo.getFunctionName(), path, functionDto.getRunEnv());
      FileUtil.del(filePath);
    } catch (Exception ex) {
      logger.error("UpdateFunction fail:", ex);
      throw new OutOfBusinessException("UpdateFunction fail:", ex);
    }
    return ResponseEntity.status(HttpStatus.OK).body(Result.success(returnFile));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteFunctionByFunctionId(String functionId) {
    if (StrUtil.isEmpty(functionId)) {
      throw new OutOfBusinessException("FunctionId cannot empty", HttpStatus.BAD_REQUEST.value());
    }
    FunctionDo functionDo = functionMapper.selectByFunctionId(functionId);
    if (functionDo == null) {
      throw new EntityNotFoundException("Function does not exist");
    }
    functionMapper.deleteFunctionByFunctionId(functionId);
    fileService.removeFile(functionId);
    managerService.DeleteFunction(functionDo.getFunctionName());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ResponseEntity creatFunction(FunctionDto functionDto) {
    //判断functionName是否重复
    Long exist = functionMapper.existFunctionByFunctionName(functionDto.getFunctionName());
    if (exist != null) {
      throw new EntityExistsException(
          "function '" + functionDto.getFunctionName() + "' already exists in service '"
              + functionDto.getServiceName() + "'");
    }
    //将Base64编码的字符串转成文件
    String filePath = baseDir + functionDto.getFunctionName() + "_code.zip";
    Boolean aBoolean = FileBase64Util.decryptByBase64(functionDto.getCode().getZipFile(), filePath);
    if (!aBoolean) {
      throw new OutOfBusinessException("File conversion failed.", HttpStatus.BAD_REQUEST.value());
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
      functionDo.setRunEnv(RunEnvEnum.valueOf(functionDto.getRunEnv()));
      this.saveFunction(functionDo);
      String path = "http://" + serverAddress + ":" + serverPort + "/functionFile/" + functionId;
      managerService.CreateFunction(functionDo.getFunctionName(), path, functionDto.getRunEnv());
      FileUtil.del(filePath);
      return ResponseEntity.status(HttpStatus.OK).body(Result.success(returnFile));
    } catch (Exception ex) {
      logger.error("CreatFunction fail:", ex);
      this.deleteFunctionByFunctionId(functionId);
      throw new OutOfBusinessException("CreatFunction fail:", ex);
    }
  }

}
