package com.igot.cb.masterdata.model;

import lombok.*;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchCriteria {
    private String search = "";        // keyword to search
    private Integer page = 0;          // page number (default 0)
    private Integer size = 10;         // page size (default 10)
    private String sortBy = "id";      // field to sort by
    private String orderBy = "ASC";    // sort direction: ASC or DESC
}


