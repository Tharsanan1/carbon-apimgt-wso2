package org.wso2.carbon.apimgt.rest.api.admin.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


import javax.validation.Valid;



public class CustomRuleListDTO   {
  
    private Integer count = null;
    private List<CustomRuleDTO> list = new ArrayList<CustomRuleDTO>();

  /**
   * Number of Custom Rules returned. 
   **/
  public CustomRuleListDTO count(Integer count) {
    this.count = count;
    return this;
  }

  
  @ApiModelProperty(example = "1", value = "Number of Custom Rules returned. ")
  @JsonProperty("count")
  public Integer getCount() {
    return count;
  }
  public void setCount(Integer count) {
    this.count = count;
  }

  /**
   **/
  public CustomRuleListDTO list(List<CustomRuleDTO> list) {
    this.list = list;
    return this;
  }

  
  @ApiModelProperty(value = "")
      @Valid
  @JsonProperty("list")
  public List<CustomRuleDTO> getList() {
    return list;
  }
  public void setList(List<CustomRuleDTO> list) {
    this.list = list;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CustomRuleListDTO customRuleList = (CustomRuleListDTO) o;
    return Objects.equals(count, customRuleList.count) &&
        Objects.equals(list, customRuleList.list);
  }

  @Override
  public int hashCode() {
    return Objects.hash(count, list);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CustomRuleListDTO {\n");
    
    sb.append("    count: ").append(toIndentedString(count)).append("\n");
    sb.append("    list: ").append(toIndentedString(list)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

