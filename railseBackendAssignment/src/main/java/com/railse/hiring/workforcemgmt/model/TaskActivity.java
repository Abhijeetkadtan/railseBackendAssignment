package com.railse.hiring.workforcemgmt.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskActivity {
    private Long taskId;
    private String description;
    private String triggeredBy;
    private Long timestamp;
}
