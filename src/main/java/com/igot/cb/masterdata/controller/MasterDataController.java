package com.igot.cb.masterdata.controller;

import com.igot.cb.masterdata.service.MasterDataService;
import com.igot.cb.util.ApiResponse;
import com.igot.cb.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author: mahesh.vakkund
 */
@RestController
@RequestMapping("/v1/masterdata")
public class MasterDataController {

    @Autowired
    MasterDataService masterDataService;

    @GetMapping(value = "/list/institutions")
    public ResponseEntity<ApiResponse> getInstitutionsList(@RequestHeader(Constants.X_AUTH_TOKEN) String authToken) {
        return new ResponseEntity<>(masterDataService.getInstitutionsList(authToken), HttpStatus.OK);
    }

    @GetMapping(value = "/list/degrees")
    public ResponseEntity<ApiResponse> getDegreesList(@RequestHeader(Constants.X_AUTH_TOKEN) String authToken) {
        return new ResponseEntity<>(masterDataService.getDegreesList(authToken), HttpStatus.OK);
    }

}