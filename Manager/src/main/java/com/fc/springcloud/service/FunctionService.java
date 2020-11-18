package com.fc.springcloud.service;

import com.fc.springcloud.entity.FunctionDo;
import com.fc.springcloud.query.FunctionQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FunctionService {
    int saveFunction(FunctionDo functionDo);

    int uploadFunction(FunctionDo functionDo);

    void deleteFunction(Long id);

    FunctionDo getFunction(Long id);

    Page<FunctionDo> listFunctionByPages(FunctionQuery functionQuery, Pageable page);

    FunctionDo getFunction(String functionName);

    int deleteFunctionByFunctionName(String functionName);
}
