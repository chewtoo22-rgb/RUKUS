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
    private val appContext=context.applicationContext
    private val controller=DeviceController(appContext)
    private val sessions=TaskSessionStore(appContext)

    fun lastSession(): PersistedTaskSession? = sessions.load()

    fun run(request:String, approved:Boolean=false):ExecutionReport {
        val plan=CommandPlanner.plan(request)
        if(plan.actions.isEmpty()) return failEarly(request,"No executable command",0)
        if(plan.rejectedParts.isNotEmpty()) return failEarly(request,"I understood part of that, but not: ${plan.rejectedParts.joinToString()}",plan.actions.size)
        return executePlan(request,plan,0,approved,false)
    }

    /** Resume from the first unverified persisted checkpoint instead of replaying verified steps. */
    fun resumeLast(approved:Boolean=false):ExecutionReport {
        val session=sessions.load() ?: return ExecutionReport(false,"No saved task session")
        val plan=CommandPlanner.plan(session.request)
        val decision=ResumePolicy.decide(session,plan)
        if(!decision.allowed) return ExecutionReport(false,decision.reason,completedSteps=session.currentStep,totalSteps=session.totalSteps)
        ActionAudit.record(session.request,null,"RESUME: ${decision.reason} step=${decision.startStep+1}/${plan.actions.size}")
        return executePlan(session.request,plan,decision.startStep,approved,true)
    }

    private fun executePlan(request:String, plan:CommandPlanner.Plan, startStep:Int, approved:Boolean, resumed:Boolean):ExecutionReport {
        val prior=sessions.load()
        val initialScreen=if(resumed) prior?.lastScreenSummary else null
        val priorRecovery=if(resumed) prior?.recoveryAttempts ?: 0 else 0
        setState(AgentTaskState(request,startStep,plan.actions.size,null,initialScreen,priorRecovery,AgentTaskState.Status.RUNNING))
        var anyRecovery=priorRecovery>0

        for(index in startStep until plan.actions.size) {
            val action=plan.actions[index]
            val before=controller.execute(AgentAction.InspectScreen).getOrNull()
            setState(AgentTaskState(request,index,plan.actions.size,action,before,if(anyRecovery)1 else 0,AgentTaskState.Status.RUNNING))
            val decision=SafetyGate.classify(action)
            if(decision.risk==Risk.BLOCKED) return terminalFailure(request,index,plan.actions.size,action,decision.reason,anyRecovery)
            if(decision.risk==Risk.CONFIRM&&!approved) {
                ActionAudit.record(request,action,"AWAITING_CONFIRMATION")
                setState(AgentTaskState(request,index,plan.actions.size,action,before,if(anyRecovery)1 else 0,AgentTaskState.Status.WAITING_CONFIRMATION))
                return ExecutionReport(false,decision.reason,action,true,index,plan.actions.size,anyRecovery)
            }

            var executedAction=action
            var result=controller.execute(executedAction)
            if(result.isFailure) {
                val firstError=result.exceptionOrNull()?.message ?: "Execution failed"
                val recovery=RecoveryPolicy.decide(executedAction,firstError)
                if(recovery.retry) {
                    anyRecovery=true
                    val screen=if(recovery.inspectFirst) controller.execute(AgentAction.InspectScreen).getOrNull() else before
                    setState(AgentTaskState(request,index,plan.actions.size,executedAction,screen,1,AgentTaskState.Status.RECOVERING))
                    ActionAudit.record(request,executedAction,"RECOVERY: ${recovery.reason}")
                    result=controller.execute(executedAction)
                }
                if(result.isFailure) {
                    val screen=controller.execute(AgentAction.InspectScreen).getOrNull()
                    val adaptive=AdaptiveRecoveryPlanner.replan(executedAction,screen,firstError)
                    if(adaptive.alternate!=null && SafetyGate.classify(adaptive.alternate).risk==Risk.SAFE) {
                        anyRecovery=true
                        executedAction=adaptive.alternate
                        ActionAudit.record(request,executedAction,"REPLAN: ${adaptive.reason} confidence=${adaptive.confidence}")
                        setState(AgentTaskState(request,index,plan.actions.size,executedAction,screen,1,AgentTaskState.Status.RECOVERING))
                        result=controller.execute(executedAction)
                    }
                }
            }

            if(result.isFailure) {
                val msg=result.exceptionOrNull()?.message ?: "Execution failed"
                return terminalFailure(request,index,plan.actions.size,executedAction,msg,anyRecovery)
            }

            var after=controller.execute(AgentAction.InspectScreen).getOrNull()
            var verify=ActionVerifier.verify(executedAction,before,after,result.getOrNull())
            if(!verify.ok) {
                ActionAudit.record(request,executedAction,"VERIFY_FAILED: ${verify.reason}")
                val adaptive=AdaptiveRecoveryPlanner.replan(executedAction,after,verify.reason)
                if(adaptive.alternate!=null && SafetyGate.classify(adaptive.alternate).risk==Risk.SAFE) {
                    anyRecovery=true
                    executedAction=adaptive.alternate
                    ActionAudit.record(request,executedAction,"VERIFY_REPLAN: ${adaptive.reason} confidence=${adaptive.confidence}")
                    setState(AgentTaskState(request,index,plan.actions.size,executedAction,after,1,AgentTaskState.Status.RECOVERING))
                    val alternateResult=controller.execute(executedAction)
                    if(alternateResult.isSuccess) {
                        val alternateAfter=controller.execute(AgentAction.InspectScreen).getOrNull()
                        val alternateVerify=ActionVerifier.verify(executedAction,after,alternateAfter,alternateResult.getOrNull())
                        if(alternateVerify.ok) {
                            result=alternateResult
                            after=alternateAfter
                            verify=alternateVerify
                        }
                    }
                }
            }

            if(!verify.ok) {
                setState(AgentTaskState(request,index,plan.actions.size,executedAction,after,if(anyRecovery)1 else 0,AgentTaskState.Status.FAILED))
                return ExecutionReport(false,"Step ${index+1}/${plan.actions.size} could not be verified after safe replanning: ${verify.reason}",executedAction,false,index,plan.actions.size,anyRecovery)
            }

            ActionAudit.record(request,executedAction,"OK+VERIFIED: ${verify.reason}")
            setState(AgentTaskState(request,index+1,plan.actions.size,executedAction,after,if(anyRecovery)1 else 0,AgentTaskState.Status.RUNNING))
        }

        val final=AgentTaskState(request,plan.actions.size,plan.actions.size,plan.actions.last(),AgentTaskStateStore.get().lastScreenSummary,if(anyRecovery)1 else 0,AgentTaskState.Status.COMPLETE)
        setState(final)
        val completedNow=plan.actions.size-startStep
        val msg=when {
            resumed && completedNow==1 -> "Resumed task; final action completed and verified"
            resumed -> "Resumed at step ${startStep+1}; ${completedNow} remaining actions completed and verified"
            plan.actions.size==1 -> "1 action completed and verified"
            else -> "${plan.actions.size} actions completed and verified"
        }
        return ExecutionReport(true,msg,plan.actions.last(),false,plan.actions.size,plan.actions.size,anyRecovery)
    }

    private fun setState(state:AgentTaskState){ AgentTaskStateStore.set(state); sessions.save(state) }

    private fun failEarly(request:String,why:String,total:Int):ExecutionReport {
        ActionAudit.record(request,null,"REJECTED: $why")
        setState(AgentTaskState(request,0,total,null,null,0,AgentTaskState.Status.FAILED))
        return ExecutionReport(false,why,totalSteps=total)
    }

    private fun terminalFailure(request:String,index:Int,total:Int,action:AgentAction,msg:String,recovered:Boolean):ExecutionReport {
        val screen=controller.execute(AgentAction.InspectScreen).getOrNull()
        ActionAudit.record(request,action,"FAILED: $msg")
        setState(AgentTaskState(request,index,total,action,screen,if(recovered)1 else 0,AgentTaskState.Status.FAILED))
        val contextText=screen?.takeIf{it.isNotBlank()}?.let{" Visible now: $it"} ?: ""
        return ExecutionReport(false,"Step ${index+1}/$total failed: $msg.$contextText",action,false,index,total,recovered)
    }
}
