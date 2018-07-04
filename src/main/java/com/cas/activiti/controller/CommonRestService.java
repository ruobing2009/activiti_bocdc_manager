package com.cas.activiti.controller;

import java.util.HashMap;
import java.util.Map;

import org.activiti.engine.delegate.BpmnError;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.el.FixedValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.JsonNode;

@SuppressWarnings("unused")
@Service("CommonRestService")
public class CommonRestService implements JavaDelegate {

	private Expression requestUrl;
	private Expression method;
	
	private Expression requestData;
	private Expression response;
	
	@Override
	public void execute(DelegateExecution execution) {
		
		String requestUrl = this.requestUrl.getValue(execution).toString();
		String method = this.method.getValue(execution).toString();
		//String params = this.params.getValue(execution).toString();
		Map<String, Object> params = new HashMap<String, Object>();
		//JSONObject body = (JSONObject) JSON.toJSON(this.requestData.getValue(execution));
		System.out.println("requestData NEW"+ this.requestData.getValue(execution).toString());
		ObjectMapper mapper = new ObjectMapper();
		JsonNode body = mapper.convertValue(this.requestData.getValue(execution).toString(), JsonNode.class);
		//String body = this.requestData.getValue(execution).toString();
		System.out.println("body NEW"+ body.toString());
		//Thread.sleep(3000);
		try {
			//System.out.println("Variables "+execution.getVariables().toString());
			HttpResponse<String> result;
			//Object result = null;
			
			HashMap<String, String> headers = new HashMap<String, String>();
			headers.put("Content-Type", "application/json");
			
			switch (method) {
				case "get":
					result = Unirest.get(requestUrl).headers(headers).queryString(params).asString();		
					break;
				case "post":
					result = Unirest.post(requestUrl).headers(headers).queryString(params).body(body).asString();
					break;
				case "put":
					result = Unirest.put(requestUrl).headers(headers).queryString(params).body(body).asString();		
					break;
				case "delete":
					result = Unirest.delete(requestUrl).headers(headers).queryString(params).body(body).asString();		
					break;
				default:
					result = Unirest.get(requestUrl).headers(headers).queryString(params).asString();		
					break;
			}
			try{
				System.out.println("response NEW"+ result.getBody().toString());
				//execution.setVariable("CommonRestServiceResult", result.getBody().toString());
				JSONObject re = (JSONObject) JSON.parseObject(result.getBody().toString());
				execution.setVariable("response", re.get("response").toString());
				try{
					execution.setVariable("response_message", re.get("response_message").toString());
				} catch(Exception er){
					execution.setVariable("response_message", "");
				}
				System.out.println("Variables "+execution.getVariables().toString());				
			} catch (Exception e) {
				try{
					execution.setVariable("response_message", result.getBody().toString());
					execution.setVariable("response", result.getBody().toString());
					System.out.println("Variables "+execution.getVariables().toString());			
				} catch(Exception err){
					System.out.println(err.getMessage());
					execution.setVariable("response", "error");
					execution.setVariable("response_message", err.getMessage());
					System.out.println("Variables "+execution.getVariables().toString());		
					new BpmnError("CommonRestServiceError", e.getMessage());
				}
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			new BpmnError("CommonRestServiceError", e.getMessage());
		}
	}
}
