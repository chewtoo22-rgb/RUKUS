package com.ruckus.agent.core

import android.content.Context
import com.ruckus.agent.control.DeviceController

data class ExecutionReport(val ok:Boolean,val message:String,val action:AgentAction?=null,val needsConfirmation:Boolean=false)

class RuckusExecutor(context:Context){
    private val controller=DeviceController(context.applicationContext)
    fun run(request:String, approved:Boolean=false):ExecutionReport {
        val parsed=CommandParser.parse(request); val action=parsed.action
        if(action==null){ ActionAudit.record(request,null,"REJECTED: ${parsed.explanation}"); return ExecutionReport(false,parsed.explanation) }
        val decision=SafetyGate.classify(action)
        if(decision.risk==Risk.BLOCKED){ ActionAudit.record(request,action,"BLOCKED: ${decision.reason}"); return ExecutionReport(false,decision.reason,action) }
        if(decision.risk==Risk.CONFIRM&&!approved){ ActionAudit.record(request,action,"AWAITING_CONFIRMATION"); return ExecutionReport(false,decision.reason,action,true) }
        val result=controller.execute(action)
        val msg=result.fold({it},{it.message?:it.javaClass.simpleName})
        ActionAudit.record(request,action,if(result.isSuccess)"OK: $msg" else "FAILED: $msg")
        return ExecutionReport(result.isSuccess,msg,action)
    }
}
