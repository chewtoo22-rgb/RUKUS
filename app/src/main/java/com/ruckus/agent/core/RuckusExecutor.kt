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
        var recoveryAttempts=if(resumed) prior?.recoveryAttempts ?: 0 else 0
        setState(AgentTaskState(request,startStep,plan.actions.size,null,initialScreen,recoveryAttempts,AgentTaskState.Status.RUNNING))

        fun reserveRecovery(action:AgentAction,index:Int,reason:String):ExecutionReport? {
            val budget=RecoveryBudget.decide(recoveryAttempts)
            if(!budget.allowed) {
                ActionAudit.record(request,action,"RECOVERY_BUDGET_EXHAUSTED: $reason")
                return terminalFailure(request,index,plan.actions.size,action,budget.reason,recoveryAttempts)
            }
            recoveryAttempts += 1
            ActionAudit.record(request,action,"RECOVERY_BUDGET: ${budget.reason} | $reason")
            return null
        }

        for(index in startStep until plan.actions.size) {
            val action=plan.actions[index]
            val before=controller.execute(AgentAction.InspectScreen).getOrNull()
            setState(AgentTaskState(request,index,plan.actions.size,action,before,recoveryAttempts,AgentTaskState.Status.RUNNING))
            val decision=SafetyGate.classify(action)
            if(decision.risk==Risk.BLOCKED) return terminalFailure(request,index,plan.actions.size,action,decision.reason,recoveryAttempts)
            if(decision.risk==Risk.CONFIRM&&!approved) {
                ActionAudit.record(request,action,"AWAITING_CONFIRMATION")
                setState(AgentTaskState(request,index,plan.actions.size,action,before,recoveryAttempts,AgentTaskState.Status.WAITING_CONFIRMATION))
                return ExecutionReport(false,decision.reason,action,true,index,plan.actions.size,recoveryAttempts>0)
            }

            var executedAction=action
            var result=controller.execute(executedAction)
            if(result.isFailure) {
                val firstError=result.exceptionOrNull()?.message ?: "Execution failed"
                val recovery=RecoveryPolicy.decide(executedAction,firstError)
                if(recovery.retry) {
                    reserveRecovery(executedAction,index,recovery.reason)?.let { return it }
                    val screen=if(recovery.inspectFirst) controller.execute(AgentAction.InspectScreen).getOrNull() else before
                    setState(AgentTaskState(request,index,plan.actions.size,executedAction,screen,recoveryAttempts,AgentTaskState.Status.RECOVERING))
                    ActionAudit.record(request,executedAction,"RECOVERY: ${recovery.reason}")
                    result=controller.execute(executedAction)
                }
                if(result.isFailure) {
                    val screen=controller.execute(AgentAction.InspectScreen).getOrNull()
                    val adaptive=AdaptiveRecoveryPlanner.replan(action,screen,firstError)
                    val alternate=adaptive.alternate
                    if(alternate!=null && SafetyGate.classify(alternate).risk==Risk.SAFE && RecoveryEquivalence.canSubstitute(action,alternate,adaptive.confidence)) {
                        reserveRecovery(alternate,index,adaptive.reason)?.let { return it }
                        executedAction=alternate
                        ActionAudit.record(request,executedAction,"REPLAN: ${adaptive.reason} confidence=${adaptive.confidence}")
                        setState(AgentTaskState(request,index,plan.actions.size,executedAction,screen,recoveryAttempts,AgentTaskState.Status.RECOVERING))
                        result=controller.execute(executedAction)
                    } else if(alternate!=null) {
                        ActionAudit.record(request,action,"REPLAN_REJECTED: safe alternate did not preserve original intent (${adaptive.reason})")
                    }
                }
            }

            if(result.isFailure) {
                val msg=result.exceptionOrNull()?.message ?: "Execution failed"
                return terminalFailure(request,index,plan.actions.size,executedAction,msg,recoveryAttempts)
            }

            var after=controller.execute(AgentAction.InspectScreen).getOrNull()
            var verify=ActionVerifier.verify(executedAction,before,after,result.getOrNull())
            if(!verify.ok) {
                ActionAudit.record(request,executedAction,"VERIFY_FAILED: ${verify.reason}")
                val adaptive=AdaptiveRecoveryPlanner.replan(action,after,verify.reason)
                val alternate=adaptive.alternate
                if(alternate!=null && SafetyGate.classify(alternate).risk==Risk.SAFE && RecoveryEquivalence.canSubstitute(action,alternate,adaptive.confidence)) {
                    reserveRecovery(alternate,index,adaptive.reason)?.let { return it }
                    executedAction=alternate
                    ActionAudit.record(request,executedAction,"VERIFY_REPLAN: ${adaptive.reason} confidence=${adaptive.confidence}")
                    setState(AgentTaskState(request,index,plan.actions.size,executedAction,after,recoveryAttempts,AgentTaskState.Status.RECOVERING))
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
                } else if(alternate!=null) {
                    ActionAudit.record(request,action,"VERIFY_REPLAN_REJECTED: alternate did not preserve original intent (${adaptive.reason})")
                }
            }

            if(!verify.ok) {
                setState(AgentTaskState(request,index,plan.actions.size,executedAction,after,recoveryAttempts,AgentTaskState.Status.FAILED))
                return ExecutionReport(false,"Step ${index+1}/${plan.actions.size} could not be verified after safe replanning: ${verify.reason}",executedAction,false,index,plan.actions.size,recoveryAttempts>0)
            }

            ActionAudit.record(request,executedAction,"OK+VERIFIED: ${verify.reason}")
            setState(AgentTaskState(request,index+1,plan.actions.size,executedAction,after,recoveryAttempts,AgentTaskState.Status.RUNNING))
        }

        var finalScreen=AgentTaskStateStore.get().lastScreenSummary
        var completion=TaskCompletionGate.evaluate(plan,plan.actions.size,finalScreen)
        if(!completion.ok) {
            val last=plan.actions.last()
            ActionAudit.record(request,last,"COMPLETION_GATE_FAILED: ${completion.reason}")
            val repair=TaskCompletionRepairPlanner.plan(last,completion.reason)
            val repairAction=repair.action
            if(repairAction!=null && SafetyGate.classify(repairAction).risk==Risk.SAFE) {
                reserveRecovery(repairAction,plan.actions.lastIndex,"Completion repair: ${repair.reason}")?.let { return it }
                val beforeRepair=finalScreen
                setState(AgentTaskState(request,plan.actions.size,plan.actions.size,repairAction,beforeRepair,recoveryAttempts,AgentTaskState.Status.RECOVERING))
                ActionAudit.record(request,repairAction,"COMPLETION_REPAIR: ${repair.reason}")
                val repairResult=controller.execute(repairAction)
                if(repairResult.isSuccess) {
                    val repairedScreen=controller.execute(AgentAction.InspectScreen).getOrNull()
                    val repairVerify=ActionVerifier.verify(repairAction,beforeRepair,repairedScreen,repairResult.getOrNull())
                    if(repairVerify.ok) {
                        finalScreen=repairedScreen
                        completion=TaskCompletionGate.evaluate(plan,plan.actions.size,finalScreen)
                        if(completion.ok) ActionAudit.record(request,repairAction,"COMPLETION_REPAIR_VERIFIED: ${completion.reason}")
                        else ActionAudit.record(request,repairAction,"COMPLETION_REPAIR_GATE_FAILED: ${completion.reason}")
                    } else {
                        ActionAudit.record(request,repairAction,"COMPLETION_REPAIR_VERIFY_FAILED: ${repairVerify.reason}")
                    }
                } else {
                    ActionAudit.record(request,repairAction,"COMPLETION_REPAIR_EXEC_FAILED: ${repairResult.exceptionOrNull()?.message ?: "Execution failed"}")
                }
            } else {
                ActionAudit.record(request,last,"COMPLETION_REPAIR_UNAVAILABLE: ${repair.reason}")
            }
        }

        if(!completion.ok) {
            val last=plan.actions.last()
            setState(AgentTaskState(request,plan.actions.size,plan.actions.size,last,finalScreen,recoveryAttempts,AgentTaskState.Status.FAILED))
            return ExecutionReport(false,"All steps ran, but task completion could not be proven after bounded repair: ${completion.reason}",last,false,plan.actions.size,plan.actions.size,recoveryAttempts>0)
        }

        val final=AgentTaskState(request,plan.actions.size,plan.actions.size,plan.actions.last(),finalScreen,recoveryAttempts,AgentTaskState.Status.COMPLETE)
        setState(final)
        ActionAudit.record(request,plan.actions.last(),"TASK_COMPLETE: ${completion.reason}")
        val completedNow=plan.actions.size-startStep
        val msg=when {
            resumed && completedNow==1 -> "Resumed task; final action completed and task completion verified"
            resumed -> "Resumed at step ${startStep+1}; ${completedNow} remaining actions completed and task completion verified"
            plan.actions.size==1 -> "1 action completed and task completion verified"
            else -> "${plan.actions.size} actions completed and task completion verified"
        }
        return ExecutionReport(true,msg,plan.actions.last(),false,plan.actions.size,plan.actions.size,recoveryAttempts>0)
    }

    private fun setState(state:AgentTaskState){ AgentTaskStateStore.set(state); sessions.save(state) }

    private fun failEarly(request:String,why:String,total:Int):ExecutionReport {
        ActionAudit.record(request,null,"REJECTED: $why")
        setState(AgentTaskState(request,0,total,null,null,0,AgentTaskState.Status.FAILED))
        return ExecutionReport(false,why,totalSteps=total)
    }

    private fun terminalFailure(request:String,index:Int,total:Int,action:AgentAction,msg:String,recoveryAttempts:Int):ExecutionReport {
        val screen=controller.execute(AgentAction.InspectScreen).getOrNull()
        ActionAudit.record(request,action,"FAILED: $msg")
        setState(AgentTaskState(request,index,total,action,screen,recoveryAttempts,AgentTaskState.Status.FAILED))
        val contextText=screen?.takeIf{it.isNotBlank()}?.let{" Visible now: $it"} ?: ""
        return ExecutionReport(false,"Step ${index+1}/$total failed: $msg.$contextText",action,false,index,total,recoveryAttempts>0)
    }
}
