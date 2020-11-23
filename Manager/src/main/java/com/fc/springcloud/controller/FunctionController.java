package com.fc.springcloud.controller;

import com.fc.springcloud.common.DataItem;
import com.fc.springcloud.common.DataPage;
import com.fc.springcloud.common.Result;
import com.fc.springcloud.pojo.domain.FunctionDo;
import com.fc.springcloud.pojo.query.FunctionQuery;
import com.fc.springcloud.service.FunctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("functions/")
public class FunctionController {
    @Autowired
    private FunctionService functionService;

    @GetMapping(value = "lists")
    public ResponseEntity<DataPage> listFunctions(FunctionQuery functionQuery, Pageable pageable) {
        return ResponseEntity.ok(new DataPage(functionService.listFunctionByPages(functionQuery, pageable)));
    }

    @PostMapping(value = "save")
    public ResponseEntity SaveFunction(@RequestBody FunctionDo functionDo) {
        functionService.saveFunction(functionDo);
        return ResponseEntity.ok().body(Result.success());
    }

    @PutMapping(value = "update")
    public ResponseEntity<DataItem> updateFunction(@RequestBody FunctionDo functionDo) {
        return ResponseEntity.ok(new DataItem(functionService.uploadFunction(functionDo)));
    }

    @DeleteMapping(value = "delete/{id}")
    public ResponseEntity deleteFunction(@PathVariable Long id) {
        functionService.deleteFunction(id);
        return ResponseEntity.ok().body(Result.success());
    }

    @DeleteMapping(value = "deleteByName")
    public ResponseEntity deleteFunctionByFunctionName(String functionName) {
        functionService.deleteFunctionByFunctionName(functionName);
        return ResponseEntity.ok().body(Result.success());
    }

    @GetMapping(value = "functionName")
    public ResponseEntity getFunctionByFunctionName(String functionName) {
        functionService.getFunction(functionName);
        return ResponseEntity.ok().body(Result.success());
    }
}
