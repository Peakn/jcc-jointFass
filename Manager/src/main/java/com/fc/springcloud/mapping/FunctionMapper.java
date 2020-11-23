package com.fc.springcloud.mapping;


import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.query.FunctionQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FunctionMapper {
    int deleteByPrimaryKey(Long id);

    int insert(FunctionDo record);

    int insertSelective(FunctionDo record);

    FunctionDo selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(FunctionDo record);

    int updateByPrimaryKey(FunctionDo record);

    FunctionDo selectByFunctionName(String functionName);

    List<FunctionDo> listFunctionByPages(FunctionQuery functionQuery);

    int deleteFunctionByFunctionName(String functionName);

    int deleteFunctionByFunctionId(String functionId);

    FunctionDo selectByFunctionId(String functionId);

    Long existFunctionByFunctionName(String functionName);

}