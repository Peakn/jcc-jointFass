package com.fc.springcloud.controller;

import com.fc.springcloud.pojo.dto.ApplicationDto;
import com.fc.springcloud.service.ApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/application")
public class ApplicationController {

  @Autowired
  ApplicationService applicationService;

  @RequestMapping(value = "/", method = { RequestMethod.POST, RequestMethod.OPTIONS }, produces = "application/json;charset=UTF-8")
  public ResponseEntity<?> Create(@RequestBody ApplicationDto request) {
    try {
      System.out.println(request.toString());
      applicationService.CreateApplication(request.getName(), request.getEntryStep(), request.getSteps());
    } catch (Exception e) {
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/{name}", method = { RequestMethod.DELETE, RequestMethod.OPTIONS }, produces = "application/json;charset=UTF-8")
  public ResponseEntity<?> Delete(@PathVariable("name") String name) {
    applicationService.DeleteApplication(name);
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @RequestMapping(value = "/list", method = { RequestMethod.GET, RequestMethod.OPTIONS }, produces = "application/json;charset=UTF-8")
  public ResponseEntity<?> List() {
    return null;
  }
}
