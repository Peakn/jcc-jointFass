package com.fc.springcloud.service;

import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.dto.FunctionDto;
import com.fc.springcloud.pojo.query.FunctionQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

public interface FunctionService {
    int saveFunction(FunctionDo functionDo);

    int uploadFunction(FunctionDo functionDo);

    void deleteFunction(Long id);

    FunctionDo getFunction(Long id);

    Page<FunctionDo> listFunctionByPages(FunctionQuery functionQuery, Pageable page);

    FunctionDo getFunction(String functionName);

    int deleteFunctionByFunctionName(String functionName);

    void deleteFunctionByFunctionId(String functionId);

    ResponseEntity updateFunction(FunctionDto functionDto);

    ResponseEntity creatFunction(FunctionDto functionDto);
}
