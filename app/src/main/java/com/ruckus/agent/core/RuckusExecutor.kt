package com.ruckus.agent.core

import android.content.Context
import com.ruckus.agent.control.DeviceController

data class ExecutionReport(
    val ok:Boolean,
    val message:String,
    val action:AgentAction?=null,
    val needsConfirmation:Boolean=false,
    val completedSteps:Int=0,
    val totalSteps:Int=0,
    val recovered:Boolean=false
)

class RuckusExecutor(context:Context){
    private val controller=DeviceController(context.applicationContext)

    fun run(request:String, approved:Boolean=false):ExecutionReport {
        val plan=CommandPlanner.plan(request)
        if(plan.actions.isEmpty()) {
            val why=if(plan.rejectedParts.isEmpty()) "No executable command" else "Didn't understand: ${plan.rejectedParts.joinToString()}"
            ActionAudit.record(request,null,"REJECTED: $why")
            AgentTaskStateStore.set(AgentTaskState(request,0,0,null,null,0,AgentTaskState.Status.FAILED))
            return ExecutionReport(false,why,totalSteps=0)
        }
        if(plan.rejectedParts.isNotEmpty()) {
            val why="I understood part of that, but not: ${plan.rejectedParts.joinToString()}"
            ActionAudit.record(request,null,"REJECTED_PARTIAL: $why")
            AgentTaskStateStore.set(AgentTaskState(request,0,plan.actions.size,null,null,0,AgentTaskState.Status.FAILED))
            return ExecutionReport(false,why,totalSteps=plan.actions.size)
        }

        AgentTaskStateStore.set(AgentTaskState(request,0,plan.actions.size,null,null,0,AgentTaskState.Status.RUNNING))
        var anyRecovery=false

        for((index,action) in plan.actions.withIndex()) {
            AgentTaskStateStore.set(AgentTaskState(request,index,plan.actions.size,action,AgentTaskStateStore.get().lastScreenSummary,0,AgentTaskState.Status.RUNNING))
            val decision=SafetyGate.classify(action)
            if(decision.risk==Risk.BLOCKED) {
                ActionAudit.record(request,action,"BLOCKED: ${decision.reason}")
                AgentTaskStateStore.set(AgentTaskState(request,index,plan.actions.size,action,null,0,AgentTaskState.Status.FAILED))
                return ExecutionReport(false,decision.reason,action,false,index,plan.actions.size,anyRecovery)
            }
            if(decision.risk==Risk.CONFIRM&&!approved) {
                ActionAudit.record(request,action,"AWAITING_CONFIRMATION")
                AgentTaskStateStore.set(AgentTaskState(request,index,plan.actions.size,action,null,0,AgentTaskState.Status.WAITING_CONFIRMATION))
                return ExecutionReport(false,decision.reason,action,true,index,plan.actions.size,anyRecovery)
            }

            var result=controller.execute(action)
            if(result.isFailure) {
                val firstError=result.exceptionOrNull()?.message ?: "Execution failed"
                val recovery=RecoveryPolicy.decide(action,firstError)
                if(recovery.retry) {
                    anyRecovery=true
                    var screen:String?=null
                    if(recovery.inspectFirst) screen=controller.execute(AgentAction.InspectScreen).getOrNull()
                    AgentTaskStateStore.set(AgentTaskState(request,index,plan.actions.size,action,screen,1,AgentTaskState.Status.RECOVERING))
                    ActionAudit.record(request,action,"RECOVERY: ${recovery.reason}${screen?.let { " | screen=$it" } ?: ""}")
                    result=controller.execute(action)
                }
            }

            if(result.isFailure) {
                val msg=result.exceptionOrNull()?.message ?: "Execution failed"
                val screen=controller.execute(AgentAction.InspectScreen).getOrNull()
                ActionAudit.record(request,action,"FAILED_AFTER_RECOVERY: $msg")
                AgentTaskStateStore.set(AgentTaskState(request,index,plan.actions.size,action,screen,if(anyRecovery)1 else 0,AgentTaskState.Status.FAILED))
                val contextText=screen?.takeIf{it.isNotBlank()}?.let{" Visible now: $it"} ?: ""
                return ExecutionReport(false,"Step ${index+1}/${plan.actions.size} failed after safe recovery: $msg.$contextText",action,false,index,plan.actions.size,anyRecovery)
            }
            ActionAudit.record(request,action,"OK: ${result.getOrNull()}")
        }
        AgentTaskStateStore.set(AgentTaskState(request,plan.actions.size,plan.actions.size,plan.actions.last(),AgentTaskStateStore.get().lastScreenSummary,0,AgentTaskState.Status.COMPLETE))
        val msg=if(plan.actions.size==1) "1 action completed" else "${plan.actions.size} actions completed"
        return ExecutionReport(true,msg,plan.actions.last(),false,plan.actions.size,plan.actions.size,anyRecovery)
    }
}
