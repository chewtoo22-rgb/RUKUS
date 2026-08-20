package com.ruckus.agent.core

import android.content.Context
import com.ruckus.agent.control.DeviceController

data class ExecutionReport(
    val ok:Boolean,
    val message:String,
    val action:AgentAction?=null,
    val needsConfirmation:Boolean=false,
    val completedSteps:Int=0,
    val totalSteps:Int=0
)

class RuckusExecutor(context:Context){
    private val controller=DeviceController(context.applicationContext)

    fun run(request:String, approved:Boolean=false):ExecutionReport {
        val plan=CommandPlanner.plan(request)
        if(plan.actions.isEmpty()) {
            val why=if(plan.rejectedParts.isEmpty()) "No executable command" else "Didn't understand: ${plan.rejectedParts.joinToString()}"
            ActionAudit.record(request,null,"REJECTED: $why")
            return ExecutionReport(false,why,totalSteps=0)
        }
        if(plan.rejectedParts.isNotEmpty()) {
            val why="I understood part of that, but not: ${plan.rejectedParts.joinToString()}"
            ActionAudit.record(request,null,"REJECTED_PARTIAL: $why")
            return ExecutionReport(false,why,totalSteps=plan.actions.size)
        }

        for((index,action) in plan.actions.withIndex()) {
            val decision=SafetyGate.classify(action)
            if(decision.risk==Risk.BLOCKED) {
                ActionAudit.record(request,action,"BLOCKED: ${decision.reason}")
                return ExecutionReport(false,decision.reason,action,false,index,plan.actions.size)
            }
            if(decision.risk==Risk.CONFIRM&&!approved) {
                ActionAudit.record(request,action,"AWAITING_CONFIRMATION")
                return ExecutionReport(false,decision.reason,action,true,index,plan.actions.size)
            }
            val result=controller.execute(action)
            if(result.isFailure) {
                val msg=result.exceptionOrNull()?.message ?: "Execution failed"
                ActionAudit.record(request,action,"FAILED: $msg")
                return ExecutionReport(false,"Step ${index+1}/${plan.actions.size} failed: $msg",action,false,index,plan.actions.size)
            }
            ActionAudit.record(request,action,"OK: ${result.getOrNull()}")
        }
        val msg=if(plan.actions.size==1) "${plan.actions.size} action completed" else "${plan.actions.size} actions completed"
        return ExecutionReport(true,msg,plan.actions.last(),false,plan.actions.size,plan.actions.size)
    }
}
