package com.fc.springcloud.pojo.dto;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

public class CodeBase64 implements Serializable {
    @NotBlank(message = "zipFile cannot empty")
    private String zipFile;

    public String getZipFile() {
        return zipFile;
    }

    public void setZipFile(String zipFile) {
        this.zipFile = zipFile;
    }
}
