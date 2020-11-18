package com.fc.springcloud.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document
public class FunctionFileDocument {
    /**
     * 主键
     */
    @Id
    private String id;
    /**
     * 函数id
     */
    private String functionId;
    /**
     * 文件名称
     */
    private String name;
    /**
     * 文件类型
     */
    private String contentType;
    /**
     * 文件大小
     */
    private Long size;
    /**
     * 上传日期
     */
    private LocalDateTime uploadDate;
    /**
     * 文件MD5
     */
    private String md5;
    /**
     * 文件内容
     */
    private byte[] content;
    /**
     * 文件后缀名
     */
    private String suffix;
    /**
     * 文件描述
     */
    private String description;
    /**
     * 大文件管理GridFS的ID
     */
    private String gridFsId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGridFsId() {
        return gridFsId;
    }

    public void setGridFsId(String gridFsId) {
        this.gridFsId = gridFsId;
    }
}
