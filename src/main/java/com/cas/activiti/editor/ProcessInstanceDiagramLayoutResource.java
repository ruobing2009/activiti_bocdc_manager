package com.cas.activiti.editor;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * diagram-viewer源码
 * @author wby
 *
 */
@RestController
@RequestMapping(value = "/service")
public class ProcessInstanceDiagramLayoutResource extends BaseProcessDefinitionDiagramLayoutResource {

  @RequestMapping(value="/process-instance/{processInstanceId}/diagram-layout", method = RequestMethod.GET, produces = "application/json")
  public ObjectNode getDiagram(@PathVariable String processInstanceId) {
    return getDiagramNode(processInstanceId, null);
  }
}

