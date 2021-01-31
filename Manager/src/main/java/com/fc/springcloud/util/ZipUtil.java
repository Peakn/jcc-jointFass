package com.fc.springcloud.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
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

  public static File Merge(File resourceCode, File fileWrapper) throws IOException {
    ZipFile wrapper = new ZipFile(fileWrapper);
    ZipFile zipSource = new ZipFile(resourceCode);
    File result = File.createTempFile(resourceCode.getName(), null);
    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(result));
    Enumeration<? extends ZipEntry> wrapperEntries = wrapper.entries();
    while(wrapperEntries.hasMoreElements()) {
      ZipEntry entry = wrapperEntries.nextElement();
      out.putNextEntry(new ZipEntry(entry.getName()));
      int len;
      byte[] buf = new byte[4096];
      InputStream in = wrapper.getInputStream(entry);
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
    }

    Enumeration<? extends ZipEntry> entries = new ZipFile(resourceCode).entries();
    while(entries.hasMoreElements()) {
      // todo thinking about different version module conflict
      ZipEntry entry = entries.nextElement();
      try {
        out.putNextEntry(new ZipEntry(entry.getName()));
      } catch (ZipException e) {
        continue;
      }
      int len;
      byte[] buf = new byte[4096];
      InputStream in = zipSource.getInputStream(entry);
      while ((len = in.read(buf)) > 0) {
        out.write(buf, 0, len);
      }
    }
    out.close();
    return result;
  }


  public static File Mergev2(File resourceCode, File fileWrapper) throws IOException {
    File result = File.createTempFile(resourceCode.getName(), null);
    FileUtils.copyFile(fileWrapper, result);
    Path tempDirectory = Files.createTempDirectory(resourceCode.getName());
    new net.lingala.zip4j.ZipFile(resourceCode).extractAll(tempDirectory.toString());
    ZipParameters parameters = new ZipParameters();
    parameters.setIncludeRootFolder(true);
    net.lingala.zip4j.ZipFile resultZip = new net.lingala.zip4j.ZipFile(result);
    resultZip.addFolder(tempDirectory.toFile(), parameters);
    System.out.println(tempDirectory.getFileName().toString());
    FileHeader fileHeader = resultZip.getFileHeader(tempDirectory.getFileName().toString() + "/");
    resultZip.renameFile(fileHeader, "index/");
    tempDirectory.toFile().delete();
    return result;
  }
}
