package org.luke.learn.javabytecode;


import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.*;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.Results;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;

/**
 * 仅针对struts action 中的Result注解，包含Results中的和独立的Result
 * <p>
 * 用于Struts2 JAR 升级，新版本中Result注解中的name属性变为数组了，因此要成功启动，需要重新编译。
 * 本工具是用于无需重新编译，直接修改class文件，将Result注解中name属性修改为数组
 *
 * @author liyong
 * @version V1.0
 */
public class Struts2ResultAnnotationUpgradeUtils {
    private final static Logger log = LoggerFactory.getLogger(Struts2ResultAnnotationUpgradeUtils.class);

    public static void main(String[] args) {
        String path = "D:\\temp\\hb\\classes";
        String targetRootPath = "D:\\temp\\hb\\newClass";
        Struts2ResultAnnotationUpgradeUtils.modifyStrutsActionClass(path, targetRootPath);

    }

    /**
     * @param sourcePath 原有的Class文件的根目录
     * @param targetPath 新生成的Class文件的根目录
     */
    public static void modifyStrutsActionClass(String sourcePath, String targetPath) {
        List<File> strutsActionClassFiles = StrutsActionClassUtils.getStrutsActionClassFiles(sourcePath);
        for (File strutsActionClassFile : strutsActionClassFiles) {
            String className = StrutsActionClassUtils.getClassName(strutsActionClassFile, sourcePath);
            modifyClassAndCreateNew(className, sourcePath, targetPath);
        }
    }


    public static void modifyClassAndCreateNew(String className, String classPath, String targetRootPath) {
        ClassFile classFile = modifyClass(className, classPath);
        if (classFile == null) {
            return;
        }
        String name = classFile.getName();
        String fileName = targetRootPath + File.separator + name.replace(".", File.separator) + ".class";
        File file = new File(fileName);
        boolean mkdirs = file.getParentFile().mkdirs();
        log.debug("mkDirs {}", mkdirs);

        DataOutputStream dataOutputStream;
        try {
            dataOutputStream = new DataOutputStream(Files.newOutputStream(file.toPath()));
            classFile.write(dataOutputStream);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    public static ClassFile modifyClass(String className, String classPath) {
        ClassFile classFile = null;
        try {
            ClassPool classPool = ClassPool.getDefault();
            classPool.insertClassPath(classPath);
            CtClass clazz = classPool.getCtClass(className);
            classFile = clazz.getClassFile();
            ConstPool constPool = classFile.getConstPool();
            Object[] annotations = clazz.getAnnotations();

            log.debug("===");

            AnnotationsAttribute attribute = (AnnotationsAttribute) classFile.getAttribute(AnnotationsAttribute.visibleTag);
            for (Object annotationInst : annotations) {
                InvocationHandler invocationHandler = Proxy.getInvocationHandler(annotationInst);
                if (invocationHandler instanceof AnnotationImpl) {
                    AnnotationImpl annotationImpl = (AnnotationImpl) invocationHandler;
                    Annotation annotation1 = annotationImpl.getAnnotation();
                    Annotation newAnnotation = modifyResultAnnotation(constPool, annotation1);
                    System.out.println(annotation1.getTypeName());
                    attribute.addAnnotation(newAnnotation);
                    classFile.addAttribute(attribute);

                }
            }
            //struts Action注解可以放在Method上且results属性可以放入Result注解组，所以需要对方法上有results属性的Action注解进行处理
            handleActionOnMethod(clazz, constPool);
            clazz.detach(); //注销class
            return classFile;
        } catch (Exception e) {
            System.out.println("className:" + className);
            e.printStackTrace();
        }
        return classFile;
    }

    private static void handleActionOnMethod(CtClass clazz, ConstPool constPool) throws ClassNotFoundException {
        CtMethod[] declaredMethods = clazz.getDeclaredMethods();
        for (CtMethod declaredMethod : declaredMethods) {
            //Object[] methodAnnotations = declaredMethod.getAnnotations();
            MethodInfo methodInfo = declaredMethod.getMethodInfo();
            AnnotationsAttribute attribute = (AnnotationsAttribute) methodInfo.getAttribute(AnnotationsAttribute.visibleTag);
            log.debug("方法名:{}", methodInfo.getName());
            if (attribute == null) {
                log.debug("方法上无运行时注解 方法名:{}", methodInfo.getName());
                continue;
            }
            Annotation annotation = attribute.getAnnotation(Action.class.getTypeName());
            if (annotation == null) {
                log.debug("方法上无Action注解，所以不处理");
                continue;
            }

            MemberValue results = annotation.getMemberValue("results");
            if (results != null) {
                log.info("方法名:{} {}", methodInfo.getName(), annotation);
                log.debug("注解:{}", annotation.getTypeName());

                ArrayMemberValue resultAnnotations = (ArrayMemberValue) results;
                int index = 0;
                AnnotationMemberValue[] newResultAnnotations = new AnnotationMemberValue[resultAnnotations.getValue().length];
                for (MemberValue memberValue : resultAnnotations.getValue()) {
                    log.debug(memberValue.toString());
                    AnnotationMemberValue annotationMemberValue = (AnnotationMemberValue) memberValue;
                    Annotation resultAnnotation = annotationMemberValue.getValue();
                    Annotation newResultAnnotation = reBuildResultAnnotation(constPool, resultAnnotation);
                    newResultAnnotations[index] = new AnnotationMemberValue(newResultAnnotation, constPool);
                    index++;

                }
                ((ArrayMemberValue) results).setValue(newResultAnnotations);
            }
            attribute.addAnnotation(annotation);
            methodInfo.addAttribute(attribute);


        }
    }


    public static Annotation modifyResultAnnotation(ConstPool constpool, Annotation annotation) {
        String annotationTypeName = annotation.getTypeName();
        if (annotationTypeName.equals(Results.class.getName())) {
            MemberValue value = annotation.getMemberValue("value");
            ArrayMemberValue results = (ArrayMemberValue) value;
            MemberValue[] resultsValue = results.getValue();
            AnnotationMemberValue[] resultAnnotations = new AnnotationMemberValue[resultsValue.length];
            int index = 0;
            for (MemberValue memberValue : resultsValue) {
                AnnotationMemberValue annotationMemberValue = (AnnotationMemberValue) memberValue;
                Annotation resultAnnotation = annotationMemberValue.getValue();
                Annotation newResultAnnotation = reBuildResultAnnotation(constpool, resultAnnotation);
                resultAnnotations[index] = new AnnotationMemberValue(newResultAnnotation, constpool);
                index++;
            }
            ArrayMemberValue resultArray = new ArrayMemberValue(constpool);
            resultArray.setValue(resultAnnotations);
            annotation.addMemberValue("value", resultArray);

        }
        if (annotationTypeName.equals(Result.class.getName())) {
            return reBuildResultAnnotation(constpool, annotation);

        }
        if (annotationTypeName.equals(Action.class.getName())) {
            MemberValue value = annotation.getMemberValue("results");
            System.out.println(value);
        }
        return annotation;
    }

    private static Annotation reBuildResultAnnotation(ConstPool constpool, Annotation annotation) {
        String annotationTypeName = annotation.getTypeName();
        if (annotationTypeName.equals(Result.class.getName())) {
            MemberValue name = annotation.getMemberValue("name");
            StringMemberValue[] resultNames = new StringMemberValue[1];
            resultNames[0] = (StringMemberValue) name;
            ArrayMemberValue resultNameArray = new ArrayMemberValue(constpool);
            resultNameArray.setValue(resultNames);
            annotation.addMemberValue("name", resultNameArray);

        }
        return annotation;
    }

}
