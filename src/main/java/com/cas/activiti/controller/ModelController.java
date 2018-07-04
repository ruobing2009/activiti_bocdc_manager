package com.cas.activiti.controller;

import com.cas.activiti.common.BackUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.Model;
import org.activiti.explorer.util.XmlUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程的模型Model操作相关
 * @author wby
 *
 */
@Api(description = "流程模型Model操作相关", tags = {"modeler"})
@RestController
@RequestMapping("/models")
public class ModelController {
	private Logger logger = LoggerFactory.getLogger(getClass());
	
    @Autowired
    private ProcessEngine processEngine;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RepositoryService repositoryService;

    /**
     * 新建一个空模型
     *
     * @return
     * @throws UnsupportedEncodingException
     */
    @ApiOperation(value = "新建一个空模型")
    @GetMapping(value = "/create")
    public Map<String,Object> newModel() throws UnsupportedEncodingException {
        //初始化一个空模型
        Model model = repositoryService.newModel();

        //设置一些默认信息，可以用参数接收
        String name = "new-process";
        String description = "";
        int revision = 1;
        String key = "process";

        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

        model.setName(name);
        model.setKey(key);
        model.setMetaInfo(modelNode.toString());

        repositoryService.saveModel(model);
        String modelId = model.getId();

        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace",
                "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(modelId, editorNode.toString().getBytes("utf-8"));
//        return new ModelAndView("redirect:/modeler.html?modelId=" + id);
        return BackUtil.success();
    }

    /**
     * 获取所有模型
     *
     * @return
     */
    @ApiOperation(value = "获取所有模型")
    @GetMapping(value = "/listAll")
    public List<Model> modelList() {
       // RepositoryService repositoryService = processEngine.getRepositoryService();
    	 List<Model> list = repositoryService
                 .createModelQuery()
                 .orderByModelId()
                 .desc().list();
    	 
        return list;
    }

    
    /**
     * 导出model对象为指定类型
     *
     * @param modelId 模型ID
     * @param type    导出文件类型(bpmn\json)
     */
    @ApiOperation(value = "导出模型为bpmn或json")
    @PostMapping(value = "/export/{modelId}/{type}")
    public Object export(@PathVariable("modelId") String modelId,
                       @PathVariable("type") String type,
                       HttpServletResponse response) {
        try {
            Model modelData = repositoryService.getModel(modelId);
            BpmnJsonConverter jsonConverter = new BpmnJsonConverter();
            byte[] modelEditorSource = repositoryService.getModelEditorSource(modelData.getId());

            JsonNode editorNode = new ObjectMapper().readTree(modelEditorSource);
            BpmnModel bpmnModel = jsonConverter.convertToBpmnModel(editorNode);

            // 处理异常
            if (bpmnModel.getMainProcess() == null) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                response.getOutputStream().println("模型尚未建立一条主线流程，不能导出: " + type + "类型");
                response.flushBuffer();
                return BackUtil.failed("模型尚未建立一条主线流程，不能导出！");
            }

            String filename = "";
            byte[] exportBytes = null;

            String mainProcessId = bpmnModel.getMainProcess().getId();

            if (type.equals("bpmn")) {

                BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
                exportBytes = xmlConverter.convertToXML(bpmnModel);

                filename = mainProcessId + ".bpmn20.xml";
            } else if (type.equals("json")) {

                exportBytes = modelEditorSource;
                filename = mainProcessId + ".json";

            }

            assert exportBytes != null;
            ByteArrayInputStream in = new ByteArrayInputStream(exportBytes);
            IOUtils.copy(in, response.getOutputStream());

            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
            response.flushBuffer();
        } catch (Exception e) {
            logger.error("导出model的xml文件失败：modelId={}, type={}", modelId, type, e);
            return BackUtil.failed("导出model的xml文件失败！");
        }
        return BackUtil.success();
    }
    
    
    /**
     * 删除模型
     *
     * @param modelId
     * @return
     */
    @ApiOperation(value = "删除模型")
    @DeleteMapping(value = "/delete/{modelId}")
    public Object deleteModel(@PathVariable("modelId") String modelId) {
        RepositoryService repositoryService = processEngine.getRepositoryService();
        repositoryService.deleteModel(modelId);
        return BackUtil.success();
    }

    /**
     * 发布模型为流程定义，根据Model部署流程,图画错了会失败
     *
     * @param modelId
     * @return
     * @throws Exception
     */
    @ApiOperation(value = "发布模型为流程定义")
    @PostMapping(value = "/deployment/{modelId}")
	public Object deploy(@PathVariable("modelId") String modelId) throws Exception {
		try {
			// 获取模型
			Model modelData = repositoryService.getModel(modelId);
			byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

			if (bytes == null) {
				return BackUtil.failed("模型数据为空，请先设计流程并成功保存，再进行发布。");
			}

			JsonNode modelNode = new ObjectMapper().readTree(bytes);

			BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
			if (model.getProcesses().size() == 0) {
				return BackUtil.failed("模型数据不符要求，请至少设计一条主线流程。");
			}
			byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

			// 发布流程
			String processName = modelData.getName() + ".bpmn20.xml";
			DeploymentBuilder deploymentBuilder = repositoryService.createDeployment().name(modelData.getName())
					.addString(processName, new String(bpmnBytes, "UTF-8"));

			Deployment deployment = deploymentBuilder.deploy();
			modelData.setDeploymentId(deployment.getId());
			repositoryService.saveModel(modelData);
		} catch (Exception e) {
			logger.error("根据模型部署流程失败：modelId={}", modelId, e);
			return BackUtil.failed("根据模型部署流程失败");
		}
		return BackUtil.success();
	}
    
    /**
     * 通过文件上传已有模型
     * @param uploadfile
     * @return
     */
    @ApiOperation(value = "上传一个已有模型")
    @PostMapping(value = "/uploadFile")
    public Object deployUploadedFile(
            @RequestParam("uploadfile") MultipartFile uploadfile) {
        InputStreamReader in = null;
        try {
            try {
                boolean validFile = false;
                String fileName = uploadfile.getOriginalFilename();
                if (fileName.endsWith(".bpmn20.xml") || fileName.endsWith(".bpmn")) {
                    validFile = true;

                    XMLInputFactory xif = XmlUtil.createSafeXmlInputFactory();
                    in = new InputStreamReader(new ByteArrayInputStream(uploadfile.getBytes()), "UTF-8");
                    XMLStreamReader xtr = xif.createXMLStreamReader(in);
                    BpmnModel bpmnModel = new BpmnXMLConverter().convertToBpmnModel(xtr);

                    if (bpmnModel.getMainProcess() == null || bpmnModel.getMainProcess().getId() == null) {
                        System.out.println("err1");
                        logger.error("模型文件上传失败，请至少设计一条主线流程，model-err1");
                        return BackUtil.failed("模型文件上传失败，请至少设计一条主线流程。");
                    } else {
                        if (bpmnModel.getLocationMap().isEmpty()) {
                            System.out.println("err2");
                            logger.error("模型文件上传失败，model-err2");
                            return BackUtil.failed("模型文件上传失败");
                        } else {

                            String processName = null;
                            if (StringUtils.isNotEmpty(bpmnModel.getMainProcess().getName())) {
                                processName = bpmnModel.getMainProcess().getName();
                            } else {
                                processName = bpmnModel.getMainProcess().getId();
                            }
                            Model modelData;
                            modelData = repositoryService.newModel();
                            ObjectNode modelObjectNode = new ObjectMapper().createObjectNode();
                            modelObjectNode.put(ModelDataJsonConstants.MODEL_NAME, processName);
                            modelObjectNode.put(ModelDataJsonConstants.MODEL_REVISION, 1);
                            modelData.setMetaInfo(modelObjectNode.toString());
                            modelData.setName(processName);

                            repositoryService.saveModel(modelData);

                            BpmnJsonConverter jsonConverter = new BpmnJsonConverter();
                            ObjectNode editorNode = jsonConverter.convertToJson(bpmnModel);

                            repositoryService.addModelEditorSource(modelData.getId(), editorNode.toString().getBytes("utf-8"));
                            return BackUtil.success();
                        }
                    }
                } else {
                    System.out.println("err3");
                    logger.error("模型文件上传失败，文件名不符合规定！应以.bpmn20.xml，.bpmn结尾，model-err4");
                    return BackUtil.failed("模型文件上传失败，文件名不符合规定！");
                }
            } catch (Exception e) {
                //String errorMsg = e.getMessage().replace(System.getProperty("line.separator"), "<br/>");
//                notificationManager.showErrorNotification(Messages.MODEL_IMPORT_FAILED, errorMsg);
                logger.error("模型文件上传失败,请检查文件内容格式！",  e);
                return BackUtil.failed("模型文件上传失败！");
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
//                    notificationManager.showErrorNotification("Server-side error", e.getMessage());
                	logger.error("上传已有模型失败",  e);
                }
            }
        }
    }

}
