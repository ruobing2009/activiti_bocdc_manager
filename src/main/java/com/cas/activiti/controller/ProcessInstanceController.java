package com.cas.activiti.controller;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.form.StartFormDataImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.image.ProcessDiagramGenerator;
import org.activiti.spring.ProcessEngineFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.cas.activiti.common.BackUtil;
import com.cas.activiti.common.DateUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.fail;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程实例的相关操作
 * 流程实例ProcessInstance:流程定义启动之后会生成相应的实例
 * 一个流程中ProcessInstance有且只能有一个，而Execution可以存在多个。
 * @author wby
 */
@Api(description = "流程实例processInstance操作相关", tags = {"processInstance"})
@RestController
@RequestMapping("/processInstance")
public class ProcessInstanceController {

    @Autowired
    private HistoryService historyService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private FormService formService;
    @Autowired
    private IdentityService identityService;
    @Autowired
    ProcessEngineFactoryBean processEngine;
    @Autowired
    private ProcessEngineConfiguration processEngineConfiguration;


    
    
    
    /**
     * 提交启动流程方法1
     * @param processDefinitionId
     * @param map  表单的数据，启动流程时前端填写的信息
     * @return
     */
    @ApiOperation(value = "提交启动流程方法a,填入表单数据")
    @PostMapping(value = "start-process-a/{processDefinitionId}")
    @ResponseBody
    public Object startProcess(@PathVariable("processDefinitionId") String processDefinitionId,
                                                         @RequestBody Map<String, String> map) {
        try {
            identityService.setAuthenticatedUserId(map.get("userId"));//填入启动流程的人。流程发起人
            formService.submitStartFormData(processDefinitionId, map);//
        } catch(Exception e) {
        	return BackUtil.failed("启动流程失败");
            
        }
        return BackUtil.success();
    }
    
    /**
     * 提交启动流程方法2
     * @param processDefinitionId
     * @return
     */
    @ApiOperation(value = "提交启动流程方法b")
    @PostMapping(value = "start-process-b/{processDefinitionId}")
    @ResponseBody
    public Object startProcess(@PathVariable("processDefinitionId") String processDefinitionId) {
        try {
            runtimeService.startProcessInstanceById(processDefinitionId);
        } catch(Exception e) {
        	return BackUtil.failed("启动流程失败");
        }
        return BackUtil.success();
    }

    /**
     * 获取运行中的流程实例
     */
    @ApiOperation(value = "获取运行中的流程实例")
    @GetMapping(value = "running/list")
    @ResponseBody
    public List<Map<String, Object>> running( ) {
        ProcessInstanceQuery query = runtimeService.createProcessInstanceQuery().
        		active().
        		orderByProcessInstanceId().
        		desc();
        
        List<ProcessInstance> list = query.list();
        List<Map<String, Object>> vos = new ArrayList<>();
        list.forEach(processDefinition -> {
            HashMap<String, Object> vo = new HashMap<>();
            vo.put("processDefinitionId", processDefinition.getProcessDefinitionId());
            vo.put("processInstanceId", processDefinition.getProcessInstanceId());
            vo.put("processDefinitionName", processDefinition.getProcessDefinitionName());
            vo.put("isSuspended", processDefinition.isSuspended());
            vo.put("deployId", processDefinition.getDeploymentId());
            vos.add(vo);
        });
        return vos;
    }

    /**
     * 已结束的流程实例
     */
    @ApiOperation(value = "已结束的流程实例")
    @GetMapping(value = "finished/list")
    @ResponseBody
    public List<Map<String, Object>> finished() {
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery().
        		orderByProcessInstanceEndTime().
        		desc().
        		finished();
        
        List<HistoricProcessInstance> list = query.list();
        List<Map<String, Object>> vos = new ArrayList<>();
        list.forEach(historicProcessInstance -> {
            HashMap<String, Object> vo = new HashMap<>();
            vo.put("processDefinitionId",historicProcessInstance.getProcessDefinitionId());
            vo.put("startTime", DateUtils.formatDate(historicProcessInstance.getEndTime(),DateUtils.PATTERN_ONE));
            vo.put("endTime", DateUtils.formatDate(historicProcessInstance.getEndTime(),DateUtils.PATTERN_ONE));
            vo.put("instanceId", historicProcessInstance.getId());
            vos.add(vo);
        });
        return vos;
    }
    
   
    /**
     * 读取带跟踪的图片
     * @param instanceId 正在执行的实例ID
     * @param response
     * @throws Exception
     */
    @ApiOperation(value = "读取带跟踪的图片")
    @PostMapping(value = "trace/auto/{instanceId}")
    public void readResource(@PathVariable("instanceId") String instanceId, HttpServletResponse response)
            throws Exception {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        List<String> activeActivityIds = runtimeService.getActiveActivityIds(instanceId);

        processEngineConfiguration = processEngine.getProcessEngineConfiguration();
        Context.setProcessEngineConfiguration((ProcessEngineConfigurationImpl) processEngineConfiguration);

        ProcessDiagramGenerator diagramGenerator = processEngineConfiguration.getProcessDiagramGenerator();
        InputStream imageStream = diagramGenerator.generateDiagram(bpmnModel, "png", activeActivityIds);

        // 输出资源内容到相应对象
        byte[] b = new byte[1024];
        int len;
        while ((len = imageStream.read(b, 0, 1024)) != -1) {
            response.getOutputStream().write(b, 0, len);
        }
    }


}
