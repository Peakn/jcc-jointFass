package com.fc.springcloud.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class ZipUtil {

  private static final Log logger = LogFactory.getLog(ZipUtil.class);

  public void addFileToZip(File fileToAdd, File zipFile)
  {
    ZipOutputStream zos = null;
    FileInputStream fis = null;
    ZipEntry ze = null;
    byte[] buffer = null;
    int len;

    try {
      zos = new ZipOutputStream(new FileOutputStream(zipFile));
    } catch (FileNotFoundException e) {
      logger.fatal(e.getMessage());
      return;
    }

    ze = new ZipEntry(fileToAdd.getName());
    try {
      zos.putNextEntry(ze);

      fis = new FileInputStream(fileToAdd);
      buffer = new byte[(int) fileToAdd.length()];

      while((len = fis.read(buffer)) > 0)
      {
        zos.write(buffer, 0, len);
      }
    } catch (IOException e) {
      logger.fatal(e.getMessage());
      return;
    }
    try {
      zos.flush();
      zos.close();
      fis.close();
    } catch (IOException e) {
      logger.fatal(e.getMessage());
    }
  }
}
