package com.fc.springcloud.pojo.dto;

import javax.validation.constraints.NotEmpty;
import java.io.Serializable;

public class CodeBase64 implements Serializable {
    @NotEmpty(message = "zipFile cannot empty")
    private String zipFile;

    public String getZipFile() {
        return zipFile;
    }

    public void setZipFile(String zipFile) {
        this.zipFile = zipFile;
    }
}
