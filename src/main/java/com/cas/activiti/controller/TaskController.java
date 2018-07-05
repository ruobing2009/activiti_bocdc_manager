package com.cas.activiti.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.activiti.engine.FormService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.identity.User;
import org.activiti.engine.impl.form.TaskFormDataImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.cas.activiti.common.BackUtil;
import com.cas.activiti.common.JsonpCallbackFilter;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;


/**
 * 用户任务分类：
 * 分为4中状态：未签收/待办理、已签收/办理中、运行中/办理中、已完成/已办结
 * 先签收（因为有可能指定多人审批），后办理
 * @author wby
 *
 */
@Api(description = "查看任务的相关操作", tags = {"task"})
@RestController
@RequestMapping("/task")
public class TaskController {
	
	private static Logger logger = LoggerFactory.getLogger(JsonpCallbackFilter.class);
	
	@Autowired
    protected TaskService taskService;
	
	@Autowired
	protected RepositoryService repositoryService;
	
	@Autowired
    private FormService formService;
	
	@Autowired
    private IdentityService identityService;
	
    protected static Map<String, ProcessDefinition> PROCESS_DEFINITION_CACHE = new HashMap<String, ProcessDefinition>();
	
	/**
	 * 根据用户id获取所有的任务列表
     * 待办任务
     */
    @ApiOperation(value = "获取待办任务列表，状态包括待签收/待办理")
    @PostMapping(value = "/listAll/{userId}")
    @ResponseBody
    public List<Map<String, Object>> todoList(@PathVariable("userId") String userId) throws Exception {
       // User user = UserUtil.getUserFromSession(session);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm");

        // 已经签收的任务
        List<Task> todoList = taskService.createTaskQuery().taskAssignee(userId).active().list();
        for (Task task : todoList) {
            String processDefinitionId = task.getProcessDefinitionId();
            ProcessDefinition processDefinition = getProcessDefinition(processDefinitionId);

            Map<String, Object> singleTask = packageTaskInfo(sdf, task, processDefinition);
            singleTask.put("status", "todo");
            result.add(singleTask);
        }

        // 等待签收的任务
        List<Task> toClaimList = taskService.createTaskQuery().taskCandidateUser(userId).active().list();
        for (Task task : toClaimList) {
            String processDefinitionId = task.getProcessDefinitionId();
            ProcessDefinition processDefinition = getProcessDefinition(processDefinitionId);

            Map<String, Object> singleTask = packageTaskInfo(sdf, task, processDefinition);
            singleTask.put("status", "claim");
            result.add(singleTask);
        }

        return result;
    }
    
    /**
     * 签收任务
     */
    @ApiOperation(value = "签收任务的动作")
    @GetMapping(value = "/claim/{taskId}/{userId}")
    public Object claim(@PathVariable("taskId") String taskId, @PathVariable("userId")String userId) {
        taskService.claim(taskId, userId);
        return BackUtil.success();
    }
    
    /**
     * 读取Task的表单，例如领导审批的时候获取可见表单
     */
    @ApiOperation(value = "读取Task的表单")
    @SuppressWarnings("unchecked")
    @GetMapping(value = "/get-form/{taskId}")
    @ResponseBody
    public Map<String, Object> findTaskForm(@PathVariable("taskId") String taskId) throws Exception {
        Map<String, Object> result = new HashMap<String, Object>();
        TaskFormDataImpl taskFormData = (TaskFormDataImpl) formService.getTaskFormData(taskId);

        // 设置task为null，否则输出json的时候会报错
        taskFormData.setTask(null);

        result.put("taskFormData", taskFormData);
    /*
     * 读取enum类型数据，用于下拉框
     */
        List<FormProperty> formProperties = taskFormData.getFormProperties();
        for (FormProperty formProperty : formProperties) {
            Map<String, String> values = (Map<String, String>) formProperty.getType().getInformation("values");
            if (values != null) {
                for (Entry<String, String> enumEntry : values.entrySet()) {
                    logger.debug("enum, key: {}, value: {}", enumEntry.getKey(), enumEntry.getValue());
                }
                result.put(formProperty.getId(), values);
            }
        }

        return result;
    }
    
    
    /**
     * 办理任务，提交task的并保存form
     * 例如审批结束，提交form表单
     */
    @ApiOperation(value = "办理任务，提交task的并保存form")
    @PostMapping(value = "/complete/{taskId}/{userId}")
    public Object completeTask(@PathVariable("taskId") String taskId, @PathVariable("userId") String userId,
    		@RequestBody Map<String, String> parameterMap) {

    	logger.debug("start form parameters: {}", parameterMap);
        try {
            identityService.setAuthenticatedUserId(userId);
            formService.submitTaskFormData(taskId, parameterMap);
        } finally {
            identityService.setAuthenticatedUserId(null);
        }
        return BackUtil.success();
    }
    
    private Map<String, Object> packageTaskInfo(SimpleDateFormat sdf, Task task, ProcessDefinition processDefinition) {
        Map<String, Object> singleTask = new HashMap<String, Object>();
        singleTask.put("taskId", task.getId());
        singleTask.put("name", task.getName());
        singleTask.put("createTime", sdf.format(task.getCreateTime()));
        singleTask.put("pdname", processDefinition.getName());
        singleTask.put("pdversion", processDefinition.getVersion());
        singleTask.put("pid", task.getProcessInstanceId());
        return singleTask;
    }

    private ProcessDefinition getProcessDefinition(String processDefinitionId) {
        ProcessDefinition processDefinition = PROCESS_DEFINITION_CACHE.get(processDefinitionId);
        if (processDefinition == null) {
            processDefinition = repositoryService.createProcessDefinitionQuery().processDefinitionId(processDefinitionId).singleResult();
            PROCESS_DEFINITION_CACHE.put(processDefinitionId, processDefinition);
        }
        return processDefinition;
    }
}
