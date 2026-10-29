package com.igot.cb.profile.service;

import com.igot.cb.profile.model.WorkflowServiceHistoryEvent;

import java.util.List;

public interface ServiceHistoryApprovalService {

    boolean processServiceHistoryApprovalEvent(List<WorkflowServiceHistoryEvent> events);
}
