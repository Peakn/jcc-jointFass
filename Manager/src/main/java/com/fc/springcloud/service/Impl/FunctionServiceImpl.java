package com.fc.springcloud.service.Impl;

import cn.hutool.core.util.IdUtil;
import com.fc.springcloud.entity.FunctionDo;
import com.fc.springcloud.exception.OutOfBusinessException;
import com.fc.springcloud.mapping.FunctionMapper;
import com.fc.springcloud.query.FunctionQuery;
import com.fc.springcloud.service.FunctionService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FunctionServiceImpl implements FunctionService {
    @Autowired
    private FunctionMapper functionMapper;

    @Override
    public int saveFunction(FunctionDo functionDo) {
        functionDo.setFunctionId(IdUtil.fastUUID());
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
}
