package com.igot.cb.masterdata.model;

import lombok.*;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchCriteria {
    private String searchString = "";
    private Integer page = 0;
    private Integer size = 10;
    private String sortBy = "id";
    private String orderBy = "ASC";
    private Integer status = 1;
}


