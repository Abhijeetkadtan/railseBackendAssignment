package com.railse.hiring.workforcemgmt.service.impl;


import com.railse.hiring.workforcemgmt.common.exception.ResourceNotFoundException;
import com.railse.hiring.workforcemgmt.dto.*;
import com.railse.hiring.workforcemgmt.mapper.ITaskManagementMapper;
import com.railse.hiring.workforcemgmt.model.TaskActivity;
import com.railse.hiring.workforcemgmt.model.TaskComment;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Priority;
import com.railse.hiring.workforcemgmt.model.enums.Task;
import com.railse.hiring.workforcemgmt.model.enums.TaskStatus;
import com.railse.hiring.workforcemgmt.repository.TaskRepository;
import com.railse.hiring.workforcemgmt.service.TaskManagementService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class TaskManagementServiceImpl implements TaskManagementService {


    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;
    private final Map<Long, List<TaskComment>> commentMap = new HashMap<>();
    private final Map<Long, List<TaskActivity>> activityMap = new HashMap<>();



    public TaskManagementServiceImpl(TaskRepository taskRepository, ITaskManagementMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
    }


    @Override
    public TaskManagementDto findTaskById(Long id) {
        TaskManagement task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        return taskMapper.modelToDto(task);
    }


    @Override
    public List<TaskManagementDto> createTasks(TaskCreateRequest createRequest) {
        List<TaskManagement> createdTasks = new ArrayList<>();
        for (TaskCreateRequest.RequestItem item : createRequest.getRequests()) {
            TaskManagement newTask = new TaskManagement();
            newTask.setReferenceId(item.getReferenceId());
            newTask.setReferenceType(item.getReferenceType());
            newTask.setTask(item.getTask());
            newTask.setAssigneeId(item.getAssigneeId());
            newTask.setPriority(item.getPriority());
            newTask.setTaskDeadlineTime(item.getTaskDeadlineTime());
            newTask.setStatus(TaskStatus.ASSIGNED);
            newTask.setDescription("New task created.");
            createdTasks.add(taskRepository.save(newTask));

            //add activity
            activityMap.computeIfAbsent(newTask.getId(), k -> new ArrayList<>())
                    .add(new TaskActivity(newTask.getId(), "Task created", "SYSTEM", System.currentTimeMillis()));
        }
        return taskMapper.modelListToDtoList(createdTasks);
    }


    @Override
    public List<TaskManagementDto> updateTasks(UpdateTaskRequest updateRequest) {
        List<TaskManagement> updatedTasks = new ArrayList<>();
        for (UpdateTaskRequest.RequestItem item : updateRequest.getRequests()) {
            TaskManagement task = taskRepository.findById(item.getTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + item.getTaskId()));


            if (item.getTaskStatus() != null) {
                task.setStatus(item.getTaskStatus());
            }
            if (item.getDescription() != null) {
                task.setDescription(item.getDescription());
            }
            updatedTasks.add(taskRepository.save(task));
        }
        return taskMapper.modelListToDtoList(updatedTasks);
    }


    @Override
    public String assignByReference(AssignByReferenceRequest request) {
        List<Task> applicableTasks = Task.getTasksByReferenceType(request.getReferenceType());
        List<TaskManagement> existingTasks = taskRepository.findByReferenceIdAndReferenceType(request.getReferenceId(), request.getReferenceType());


        for (Task taskType : applicableTasks) {
            List<TaskManagement> tasksOfType = existingTasks.stream()
                    .filter(t -> t.getTask() == taskType && t.getStatus() != TaskStatus.COMPLETED)
                    .collect(Collectors.toList());


            // BUG #1 is here. It should assign one and cancel the rest.
            // Instead, it reassigns ALL of them.
            if (!tasksOfType.isEmpty()) {
//                for (TaskManagement taskToUpdate : tasksOfType) {
//                    taskToUpdate.setAssigneeId(request.getAssigneeId());
//                    System.out.println(taskToUpdate);
//                    taskRepository.save(taskToUpdate);
//                }
                TaskManagement toAssign = tasksOfType.get(0);// for getting first task
                toAssign.setAssigneeId(request.getAssigneeId());
                toAssign.setStatus(TaskStatus.ASSIGNED);
                taskRepository.save(toAssign);

                tasksOfType.stream().skip(1).forEach(taskToCancel -> {//for cancelling the rest using loop
                    taskToCancel.setStatus(TaskStatus.CANCELLED);

                    taskRepository.save(taskToCancel);
                });
                System.out.println(toAssign);
            } else {
                // Create a new task if none exist
                TaskManagement newTask = new TaskManagement();
                newTask.setReferenceId(request.getReferenceId());
                newTask.setReferenceType(request.getReferenceType());
                newTask.setTask(taskType);
                newTask.setAssigneeId(request.getAssigneeId());
                newTask.setStatus(TaskStatus.ASSIGNED);
                taskRepository.save(newTask);
            }
        }
        return "Tasks assigned successfully for reference " + request.getReferenceId();
    }


    @Override
    public List<TaskManagementDto> fetchTasksByDate(TaskFetchByDateRequest request) {
        List<TaskManagement> tasks = taskRepository.findByAssigneeIdIn(request.getAssigneeIds());
        System.out.println("---- Tasks before filtering ----");
        tasks.forEach(task -> {
            System.out.println("Deadline: " + task.getTaskDeadlineTime());
            System.out.println("Status: " + task.getStatus());
            System.out.println("Assignee: " + task.getAssigneeId());
        });


        // BUG #2 is here. It should filter out CANCELLED tasks but doesn't.
        List<TaskManagement> filteredTasks = tasks.stream()
                .filter(task ->
//                {
//                    // This logic is incomplete for the assignment.
//                    // It should check against startDate and endDate.
//                    // For now, it just returns all tasks for the assignees.
//                    return true;
//                }
                                        task.getStatus() != TaskStatus.CANCELLED &&
                                        task.getTaskDeadlineTime() != null &&
                                        task.getTaskDeadlineTime() >= request.getStartDate() &&
                                        task.getTaskDeadlineTime() <= request.getEndDate()

                )
                .collect(Collectors.toList());


        return taskMapper.modelListToDtoList(filteredTasks);
    }

    //new features
    @Override
    public List<TaskManagementDto> fetchSmartTasksByDate(TaskFetchByDateRequest request) {
        List<TaskManagement> allTasks = taskRepository.findByAssigneeIdIn(request.getAssigneeIds());

        long startDate = request.getStartDate();
        long endDate = request.getEndDate();

        List<TaskManagement> filtered = allTasks.stream()
                .filter(task -> {
                    boolean isActive = task.getStatus() != TaskStatus.CANCELLED && task.getStatus() != TaskStatus.COMPLETED;
                    Long deadline = task.getTaskDeadlineTime();

                    if (deadline == null) return false;

                    boolean createdWithinRange = deadline >= startDate && deadline <= endDate;
                    boolean startedBeforeRangeStillActive = deadline < startDate;

                    return isActive && (createdWithinRange || startedBeforeRangeStillActive);
                })
                .collect(Collectors.toList());

        return taskMapper.modelListToDtoList(filtered);
    }


    @Override
    public void updatePriority(Long taskId, Priority priority) {
        TaskManagement task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        task.setPriority(priority);
        taskRepository.save(task);

        //new feature
        activityMap.computeIfAbsent(taskId, k -> new ArrayList<>())
                .add(new TaskActivity(taskId, "Priority changed to " + priority, "MANAGER", System.currentTimeMillis()));
    }

    @Override
    public List<TaskManagementDto> getTasksByPriority(Priority priority) {
        List<TaskManagement> allTasks = taskRepository.findAll();
        List<TaskManagement> filteredTasks = allTasks.stream()
                .filter(task -> task.getPriority() == priority)
                .collect(Collectors.toList());
        return taskMapper.modelListToDtoList(filteredTasks);
    }

    //add commend feature
    public void addComment(Long taskId, String comment, String createdBy) {
        commentMap.computeIfAbsent(taskId, k -> new ArrayList<>())
                .add(new TaskComment(taskId, comment, createdBy, System.currentTimeMillis()));
    }

    //get task details with comments
    public TaskDetailDto getTaskDetails(Long taskId) {
        TaskManagement task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        List<TaskComment> comments = commentMap.getOrDefault(taskId, new ArrayList<>());
        List<TaskActivity> activities = activityMap.getOrDefault(taskId, new ArrayList<>());

        comments.sort(Comparator.comparingLong(TaskComment::getTimestamp));
        activities.sort(Comparator.comparingLong(TaskActivity::getTimestamp));

        return new TaskDetailDto(taskMapper.modelToDto(task), comments, activities);
    }


}


