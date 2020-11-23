package com.fc.springcloud.util;

import com.fc.springcloud.exception.OutOfBusinessException;
import com.google.common.base.Strings;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileBase64Util {
    /**
     * 把base64转化为文件.
     *
     * @param base64   base64
     * @param filePath 目标文件路径
     * @return boolean isTrue
     */
    public static Boolean decryptByBase64(String base64, String filePath) {

        if (Strings.isNullOrEmpty(base64) && Strings.isNullOrEmpty(filePath)) {
            return Boolean.FALSE;
        }
        try {
            Files.write(Paths.get(filePath), Base64.decodeBase64(base64.substring(base64.indexOf(",") + 1)), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new OutOfBusinessException("Failed to convert Base64 to file", e);
        }
        return Boolean.TRUE;
    }

    /**
     * 把文件转化为base64.
     *
     * @param filePath 源文件路径
     * @return String 转化后的base64
     */
    public static String encryptToBase64(String filePath) {
        if (!Strings.isNullOrEmpty(filePath)) {
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(filePath));
                return Base64.encodeBase64String(bytes);
            } catch (IOException e) {
                throw new OutOfBusinessException("Failed to convert file to Base64", e);
            }
        }
        return null;
    }
}
