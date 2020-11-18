package com.fc.springcloud.util;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class ZipUtil {

  private static final Log logger = LogFactory.getLog(ZipUtil.class);

  public static void addFileToZip(File fileToAdd, File zipFile)
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

  public static void addFilesToExistingZip(File zipFile,
                                           File[] files) throws IOException {
    // get a temp file
    File tempFile = File.createTempFile(zipFile.getName(), null);
    // delete it, otherwise you cannot rename your existing zip to it.
    tempFile.delete();

    boolean renameOk=zipFile.renameTo(tempFile);
    if (!renameOk)
    {
      throw new RuntimeException("could not rename the file "+zipFile.getAbsolutePath()+" to "+tempFile.getAbsolutePath());
    }
    byte[] buf = new byte[1024];

    ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));

    ZipEntry entry = zin.getNextEntry();
    while (entry != null) {
      String name = entry.getName();
      boolean notInFiles = true;
      for (File f : files) {
        if (f.getName().equals(name)) {
          notInFiles = false;
          break;
        }
      }
      if (notInFiles) {
        // Add ZIP entry to output stream.
        out.putNextEntry(new ZipEntry(name));
        // Transfer bytes from the ZIP file to the output file
        int len;
        while ((len = zin.read(buf)) > 0) {
          out.write(buf, 0, len);
        }
      }
      entry = zin.getNextEntry();
    }
    // Close the streams
    zin.close();
    // Compress the files
    for (int i = 0; i < files.length; i++) {
      InputStream in = new FileInputStream(files[i]);
      // Add ZIP entry to output stream.
      out.putNextEntry(new ZipEntry(files[i].getName()));
      // Transfer bytes from the file to the ZIP file
      int len;
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
      // Complete the entry
      out.closeEntry();
      in.close();
    }
    // Complete the ZIP file
    out.close();
    tempFile.delete();
  }
}
