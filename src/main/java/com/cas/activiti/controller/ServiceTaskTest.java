package com.cas.activiti.controller;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;

public class ServiceTaskTest implements JavaDelegate{
	  public void execute(DelegateExecution execution) throws Exception {
		    String var = (String) execution.getVariable("input");
		    var = var.toUpperCase();
		    execution.setVariable("input", var);
		    System.out.println("执行ServiceTask:"+var);
	  }
}
