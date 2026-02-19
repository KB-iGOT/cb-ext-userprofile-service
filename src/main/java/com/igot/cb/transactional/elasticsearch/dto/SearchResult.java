package com.igot.cb.transactional.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult implements Serializable {

  private List<Map<String, Object>> data;
  private Map<String, List<FacetDTO>> facets;
  private long totalCount;
  private Map<String, String> userDetails;
}
