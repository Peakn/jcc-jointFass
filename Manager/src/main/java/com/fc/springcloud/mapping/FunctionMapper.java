package com.fc.springcloud.mapping;


import com.fc.springcloud.entity.FunctionDo;
import com.fc.springcloud.query.FunctionQuery;
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
}