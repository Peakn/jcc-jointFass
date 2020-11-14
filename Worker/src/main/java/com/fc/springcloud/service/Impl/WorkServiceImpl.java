package com.fc.springcloud.service.Impl;

import com.fc.springcloud.service.WorkService;
import org.springframework.stereotype.Service;

@Service
public class WorkServiceImpl implements WorkService {

    public String Test(String name) {
        return "hello " + name;
    }
}
