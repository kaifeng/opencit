/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.tag.model;

import com.intel.dcsg.cpg.io.UUID;
import com.intel.mtwilson.jersey.FilterCriteria;
import javax.ws.rs.QueryParam;

/**
 *
 * @author ssbangal
 */
public class SelectionKvAttributeFilterCriteria implements FilterCriteria<SelectionKvAttribute>{
    
    @QueryParam("id")
    public UUID id; 
    @QueryParam("nameEqualTo")
    public String nameEqualTo; 
    @QueryParam("nameContains")
    public String nameContains; 
    @QueryParam("attrNameEqualTo")
    public String attrNameEqualTo; 
    @QueryParam("attrNameContains")
    public String attrNameContains; 
    @QueryParam("attrValueContains")
    public String attrValueContains;
    @QueryParam("attrValueEqualTo")
    public String attrValueEqualTo; 
    
}