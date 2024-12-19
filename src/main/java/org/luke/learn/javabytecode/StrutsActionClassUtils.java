package org.luke.learn.javabytecode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class StrutsActionClassUtils {
    public static String getClassName(File file, String classPath) {

        return file.getPath().replace(classPath + "\\", "").replace("\\", ".").replace(".class", "");
    }

    public static List<File> getStrutsActionClassFiles(String classPath) {
        List<File> actionClassFiles = new ArrayList<File>(3096);
        List<File> files = getFiles(classPath);
        for (File file : files) {
            if ( file.getName().endsWith("Action.class")) {
                actionClassFiles.add(file);

            }
        }
        return actionClassFiles;
    }

    public static List<File> getFiles(String path) {
        List<File> files = new ArrayList<File>(3096);
        getFile(path, files);
        return files;
    }

    private static void getFile(String path, List<File> files) {
        // 获得指定文件对象
        File file = new File(path);
        // 获得该文件夹内的所有文件

        for (File value : file.listFiles()) {
            if (value.isFile()) {
                files.add(value);

            } else if (value.isDirectory()) {
                getFile(value.getPath(), files);
            }
        }
    }
}
