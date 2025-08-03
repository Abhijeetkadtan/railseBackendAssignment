package com.railse.hiring.workforcemgmt.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskComment {
    private Long taskId;
    private String comment;
    private String createdBy;
    private Long timestamp;
}
