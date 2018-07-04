package com.cas.activiti.controller;

import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.identity.User;
import org.activiti.engine.impl.form.StartFormDataImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.cas.activiti.common.BackUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * 流程定义的相关操作
 * 流程定义ProcessDefinition：部署一个流程模型之后生成，以helloworld.bpmn与helloworld.png格式存在
 * @author wby
 *
 */
@Api(description = "流程定义ProcessDefinition操作相关", tags = {"processDefinition"})
@RestController
@RequestMapping("/processDefinition")
public class ProcessDefinitionController {
	private Logger logger = LoggerFactory.getLogger(getClass());
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private FormService formService;
    @Autowired
    private IdentityService identityService;


    /**
     * 初始化启动流程，读取启动流程的表单字段来渲染start form（例如变更时间，变更原因）
     * 动态表单获取需要填写的内容 ✔️
     */
    @ApiOperation(value = "获取流程启动需要的参数")
    @PostMapping(value = "obtain-start-process-data/{processDefinitionId}")
    @ResponseBody
    public Map<String, Object> obtainStartForm(@PathVariable("processDefinitionId") String processDefinitionId) throws Exception {
        Map<String, Object> result = new HashMap<String, Object>();
        StartFormDataImpl startFormData = (StartFormDataImpl) formService.getStartFormData(processDefinitionId);
        startFormData.setProcessDefinition(null);

		/*
		 * 读取enum类型数据，用于下拉框
		 */
		List<FormProperty> formProperties = startFormData.getFormProperties();
		for (FormProperty formProperty : formProperties) {
			Map<String, String> values = (Map<String, String>) formProperty.getType().getInformation("values");
			if (values != null) {
				for (Entry<String, String> enumEntry : values.entrySet()) {
					logger.debug("enum, key: {}, value: {}", enumEntry.getKey(), enumEntry.getValue());
				}
				result.put("enum_" + formProperty.getId(), values);
			}
		}

		result.put("form", startFormData);

		return result;
    }
    /**
     * 初始化启动流程，读取启动流程的表单内容来渲染start form
     */
   /* @ApiOperation(value = "初始化启动流程，读取启动流程的表单内容来渲染start form")
    @PostMapping(value = "get-form/start/{processDefinitionId}")
    @ResponseBody
    public Object findStartForm(@PathVariable("processDefinitionId") String processDefinitionId) throws Exception {

        // 根据流程定义ID读取外置表单
        Object startForm = formService.getRenderedStartForm(processDefinitionId);

        return startForm;
    }
    
    /**
     * 读取启动流程的表单字段
     */
   /* @ApiOperation(value = "读取启动流程的表单字段")
    @PostMapping(value = "start-process/{processDefinitionId}")
    @SuppressWarnings("unchecked")
    public String submitStartFormAndStartProcessInstance(@PathVariable("processDefinitionId") String processDefinitionId, RedirectAttributes redirectAttributes,
                                                         HttpServletRequest request) {
        Map<String, String> formProperties = new HashMap<String, String>();

        // 从request中读取参数然后转换
        Map<String, String[]> parameterMap = request.getParameterMap();
        Set<Entry<String, String[]>> entrySet = parameterMap.entrySet();
        for (Entry<String, String[]> entry : entrySet) {
            String key = entry.getKey();

            // fp_的意思是form paremeter
            if (StringUtils.defaultString(key).startsWith("fp_")) {
                formProperties.put(key.split("_")[1], entry.getValue()[0]);
            }
        }

        logger.debug("start form parameters: {}", formProperties);

        try {
            //identityService.setAuthenticatedUserId(user.getId());

            ProcessInstance processInstance = formService.submitStartFormData(processDefinitionId, formProperties);
            logger.debug("start a processinstance: {}", processInstance);

            redirectAttributes.addFlashAttribute("message", "启动成功，流程ID：" + processInstance.getId());
        } finally {
            identityService.setAuthenticatedUserId(null);
        }

        return "redirect:/form/dynamic/process-list";
    }
    */
    /**
     * 查询所有的流程定义，包括各个版本
     * @param pagination
     * @return
     */
    @ApiOperation(value = "查询所有的流程定义，包括各个版本")
    @GetMapping(value = "listAll")
    @ResponseBody
	public List<Map<String, Object>> selectProcess() {
		List<ProcessDefinition> list = repositoryService.createProcessDefinitionQuery()// 创建一个流程定义查询
				.orderByDeploymentId()
				.orderByProcessDefinitionVersion().desc()// 按照版本的降序排列
				.list();// 返回一个集合列表，封装流程定义
		List<Map<String, Object>> vos = new ArrayList<>();
		list.forEach(processDefinition -> {
			HashMap<String, Object> vo = new HashMap<>();
			vo.put("processDefinitionId", processDefinition.getId());//流程定义ID :流程定义的key+版本+随机生成数
            vo.put("processDefinitionName", processDefinition.getName() ==null? 
            		processDefinition.getKey():processDefinition.getName());//流程定义名称:对应.bpmn文件中的name属性值
            vo.put("processDefinitionKey", processDefinition.getKey());//流程定义的key:对应.bpmn文件中的id属性值
            vo.put("processDefinitionVersion", processDefinition.getVersion());//流程定义的版本:当流程定义的key值相同的情况下，版本升级，默认从1开始
            vo.put("isSuspended", processDefinition.isSuspended());//是否挂起
            vo.put("deployId", processDefinition.getDeploymentId());//部署对象ID
            vo.put("xml", processDefinition.getResourceName());//资源名称bpmn文件
            vo.put("img", processDefinition.getDiagramResourceName());//资源名称png文件
            vos.add(vo);
        });
        return vos;
    }

   

    /**
     * 删除部署的流程定义，不论是否启动，都会删除，级联删除流程实例
     */
    @ApiOperation(value = "级联删除部署的流程定义")
    @PostMapping(value = "delete/{deploymentId}")
    @ResponseBody
    public Object delete(@PathVariable("deploymentId") String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);//true表示递归删除
//        repositoryService.deleteModel(modelId);
        return BackUtil.success();
    }

    /**
     * 读取资源，展示流程图片或xml
     *
     * @param processDefinitionId 流程定义
     * @param resourceType        资源类型(xml | image)
     * @throws Exception
     */
    @ApiOperation(value = "读取资源，展示流程图片或xml")
    @PostMapping(value = "resource/read")
    public void loadByDeployment(@RequestParam("processDefinitionId") String processDefinitionId, 
    		@RequestParam("resourceType") String resourceType,
                                 HttpServletResponse response) throws Exception {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery().
        		processDefinitionId(processDefinitionId).
        		singleResult();
        
        String resourceName = "";
        if (resourceType.equals("image")) {
            resourceName = processDefinition.getDiagramResourceName();
        } else if (resourceType.equals("xml")) {
            resourceName = processDefinition.getResourceName();
        }
        InputStream resourceAsStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(), resourceName);
        byte[] b = new byte[1024];
        int len = -1;
        while ((len = resourceAsStream.read(b, 0, 1024)) != -1) {
            response.getOutputStream().write(b, 0, len);
        }
    }


    /**
     * 挂起、激活流程定义，当前流程不使用的话可以将之挂起，使之不能启动流程实例。
     */
    @ApiOperation(value = "挂起、激活流程定义")
    @PostMapping(value = "updateStatus/{state}/{processDefinitionId}")
    @ResponseBody
    public Object updateState(@PathVariable("state") String state,
                                @PathVariable("processDefinitionId") String processDefinitionId) {
        if (state.equals("active")) {
            repositoryService.activateProcessDefinitionById(processDefinitionId, true, null);
        } else if (state.equals("suspend")) {
            repositoryService.suspendProcessDefinitionById(processDefinitionId, true, null);
        }
        return BackUtil.success();
    }
}
