package com.minicord.controller;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private Object error;

    public ApiResponse() {}

    public ApiResponse(boolean success, T data, Object error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        java.util.Map<String, String> errMap = new java.util.HashMap<>();
        errMap.put("code", code);
        errMap.put("message", message);
        return new ApiResponse<>(false, null, errMap);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Object getError() { return error; }
    public void setError(Object error) { this.error = error; }
}
