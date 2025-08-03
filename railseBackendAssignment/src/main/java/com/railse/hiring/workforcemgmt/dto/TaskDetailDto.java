package com.railse.hiring.workforcemgmt.dto;

import com.railse.hiring.workforcemgmt.model.TaskActivity;
import com.railse.hiring.workforcemgmt.model.TaskComment;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TaskDetailDto {
    private TaskManagementDto task;
    private List<TaskComment> comments;
    private List<TaskActivity> activities;
}
