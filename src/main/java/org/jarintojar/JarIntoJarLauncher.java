/*
 *    Copyright 2014 - 2015 Yannick Watier
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.jarintojar;

import javafx.util.Pair;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * Created by Yannick on 12/21/2015.
 */

public class JarIntoJarLauncher {

    public static void main(String[] args) {
        JarIntoJarLoader loader;

        try {
            //Read the main from the manifest
            URLClassLoader urlCl = (URLClassLoader) JarIntoJarLauncher.class.getClassLoader();
            Manifest manifest = new Manifest(urlCl.findResource("META-INF/MANIFEST.MF").openStream());
            String binaryNameMain = manifest.getMainAttributes().getValue("Entry-Point");
            String fetcherMode = manifest.getMainAttributes().getValue("Fetcher-Mode");


            if (binaryNameMain != null && !binaryNameMain.isEmpty() && fetcherMode != null && !fetcherMode.isEmpty()) {
                loader = new JarIntoJarLoader(fetcherMode);

                Class<?> main = loader.loadClass(binaryNameMain);

                Method mainMethod = main.getMethod("main", String[].class);

                String[] params = null;
                mainMethod.invoke(null, (Object) params);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}

class JarIntoJarLoader extends ClassLoader {

    private final GenericFetcher fetcher;

    public JarIntoJarLoader(String fetcherMode) {

        if ("DirectClassFetcher".equals(fetcherMode)) {
            fetcher = new DirectClassFetcher();
        } else if ("MemoryCacheFetcher".equals(fetcherMode)) {
            fetcher = new MemoryCacheFetcher();
        } else { //Invalid, set MemoryCacheFetcher
            fetcher = new MemoryCacheFetcher();
        }

        try {
            fetcher.fetcherInit(new JarInputStream(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().toURL().openStream()));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    //Entry points
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        Class<?> loadedClass = findLoadedClass(name); //Check if the class is already loaded

        if (loadedClass == null) {
            try {
                loadedClass = getClassFromCache(name); //Check in the cache
            } catch (ClassNotFoundException e) {
            }
        }

        if (loadedClass == null) {
            try {
                loadedClass = getParent().loadClass(name); //Check in the parent
            } catch (ClassNotFoundException e) {
            }
        }

        if (loadedClass == null) {
            throw new ClassNotFoundException("Unable to find the class: " + name);
        }

        return loadedClass;
    }

    private Class<?> getClassFromCache(String name) throws ClassNotFoundException {
        byte[] bytes = fetcher.getClassDataFromName(name);

        if (bytes == null) {
            throw new ClassNotFoundException("The class is not present in the cache !");
        }

        return defineClass(name, bytes, 0, bytes.length);
    }
}

/*
    Class fetchers
 */

interface GenericFetcher {
    byte[] getClassDataFromName(String name);

    void fetcherInit(final JarInputStream zis) throws IOException;
}


abstract class AbstractFetcher implements GenericFetcher {
    protected final ExecutorService executor = Executors.newFixedThreadPool(25);

    protected static byte[] concatArrays(byte[]... arrs) {

        byte[] value = null;

        if (arrs != null && arrs.length > 0) {
            int len = 0, idx = 0;

            for (byte[] arr : arrs) { //Get the length of the final array
                if (arr != null && arr.length > 0) {
                    len += arr.length;
                }
            }

            value = new byte[len];

            for (byte[] arr : arrs) { //Put the values into the array

                if (arr != null && arr.length > 0) {
                    for (byte b : arr) {
                        value[idx] = b;
                        idx++;
                    }
                }
            }
        }

        return value;
    }

    protected FileTypeEnum getFileType(byte[] bytes, String fileExt) {

        FileTypeEnum value = FileTypeEnum.UNKNOWN;

        if (bytes != null && bytes.length == 4
                && fileExt != null && !fileExt.isEmpty()) {

            for (FileTypeEnum fileTypeEnum : FileTypeEnum.values()) {
                if (fileTypeEnum != null && Arrays.equals(bytes, fileTypeEnum.getHeader()) && fileExt.equalsIgnoreCase(fileTypeEnum.getFileExt())) {
                    value = fileTypeEnum;
                }
            }
        }

        return value;
    }

    protected byte[] readInput(InputStream stream) throws IOException {
        ByteArrayOutputStream binDataBaos = new ByteArrayOutputStream();

        for (int c; (c = stream.read()) != -1; ) {
            binDataBaos.write((byte) c);
        }

        return binDataBaos.toByteArray();
    }


    protected String getFilename(String path, boolean showExt) {

        String value = null;

        if (path != null && !path.isEmpty()) {

            String[] split = path.split(Pattern.quote("/"));

            if (split.length > 0) {

                value = split[split.length - 1];

                if (!showExt) {
                    value = value.split(Pattern.quote("."))[0];
                }
            }
        }

        return value;
    }

    protected String getFilenamePart(String filename, FilenamePart part) {
        String value = null;

        if (filename != null && !filename.isEmpty() && part != null) {

            String filenameWithExt = getFilename(filename, true);

            if (filenameWithExt != null && !filenameWithExt.isEmpty()) {
                String[] split = filenameWithExt.split(Pattern.quote("."));

                if (split.length > 0) {
                    switch (part) {
                        case FILE_EXT:
                            value = split[split.length - 1];
                            break;
                        case FILENAME:
                            value = split[split.length - 2];
                            break;
                    }
                }
            }
        }

        return value;
    }

    protected String getBinaryName(String path) {
        String aPackage = getPackage(path);
        return ((!aPackage.isEmpty()) ? aPackage + "." : "") + getFilenamePart(getFilename(path, true), FilenamePart.FILENAME);
    }


    protected String getPackage(String path) {

        StringBuffer value = new StringBuffer();

        if (path != null && !path.isEmpty()) {
            List<String> list = new ArrayList<String>();
            list.addAll(Arrays.asList(path.replaceAll(Pattern.quote(getFilename(path, true)), "").split("/")));

            int size = list.size();

            for (int i = 0; i < size; i++) {

                value.append(list.get(i));
                if (i != size - 1) {
                    value.append(".");
                }
            }
        }

        return value.toString();
    }

    protected enum FilenamePart {
        FILENAME, FILE_EXT
    }

    protected enum FileTypeEnum {
        JAR("application/java-archive", "jar", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04}),
        WAR("application/x-java-war", "war", new byte[]{(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04}),
        CLASS("application/java-vm", "class", new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}),
        MANIFEST("application/x-java-manifest", "mf", new byte[]{(byte) 0x4D, (byte) 0x61, (byte) 0x6E, (byte) 0x69}),
        UNKNOWN("application/x-unknown", null, null);

        private String mimeType, fileExt;
        private byte[] header;

        FileTypeEnum(String mimeType, String fileExt, byte[] header) {
            this.mimeType = mimeType;
            this.fileExt = fileExt;
            this.header = (header != null) ? header.clone() : null;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getFileExt() {
            return fileExt;
        }

        public byte[] getHeader() {
            return (header != null) ? header.clone() : null;
        }
    }
}

//Keep all the classes in the memory (Faster access / slower start-up)
class MemoryCacheFetcher extends AbstractFetcher {

    private final Map<String, byte[]> rawClassChache;

    public MemoryCacheFetcher() {
        rawClassChache = Collections.synchronizedMap(new HashMap<String, byte[]>());
    }

    public byte[] getClassDataFromName(String name) {
        return rawClassChache.get(name);
    }

    public void fetcherInit(JarInputStream zis) throws IOException {

        putAllClassInCache(zis);
        try {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void putAllClassInCache(final JarInputStream zis) throws IOException {
        if (zis != null) {
            JarEntry ze = zis.getNextJarEntry();

            while (ze != null) {
                if (!ze.isDirectory()) {

                    String baseName = ze.getName();
                    String fileExt = getFilenamePart(baseName, FilenamePart.FILE_EXT);

                    if (fileExt != null && !fileExt.isEmpty()) {
                        //Check the 4 first bytes
                        final byte[] currentBytes = new byte[4];
                        zis.read(currentBytes, 0, 4);

                        switch (getFileType(currentBytes, fileExt)) {
                            case CLASS:
                                String binaryName = getBinaryName(baseName);
                                rawClassChache.put(binaryName, concatArrays(currentBytes, readInput(zis)));
                                break;
                            case JAR:
                                executor.execute(new Runnable() {
                                    byte[] start = currentBytes.clone();
                                    byte[] end = readInput(zis);

                                    public void run() {
                                        try {
                                            putAllClassInCache(new JarInputStream(new BufferedInputStream(new ByteArrayInputStream(concatArrays(start, end)))));
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                                break;
                        }
                    }
                }
                ze = zis.getNextJarEntry();
            }
        }
    }
}

/*
    Keep only the where the class is located  (Slower access / Faster start-up)
    1) Store the packages in a cache
    2) When we want a class
        a) We check in each cached Pojo to find the right package
        b) With the jar info, we unpack the required jar, loop and retrieve the class.
 */
class DirectClassFetcher extends AbstractFetcher {

    private final Map<String, JarPojo> cache;
    private File currentJar;

    public DirectClassFetcher() {
        try {
            currentJar = new File(DirectClassFetcher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        cache = Collections.synchronizedMap(new HashMap<String, JarPojo>());
    }

    public byte[] getClassDataFromName(String name) {

        byte[] value = null;

        for (Map.Entry<String, JarPojo> stringSetEntry : cache.entrySet()) {

            JarPojo jarPojo = stringSetEntry.getValue();
            List<Pair<String, String>> classBinName = jarPojo.getClassBinName();

            for (Pair<String, String> pair : classBinName) {

                if (name.equals(pair.getKey())) {
                    String classPath = pair.getValue();

                    try {
                        if (jarPojo.isRootJar()) {
                            value = readInput(getClass().getClassLoader().getResourceAsStream(classPath));
                        } else { //Inner jar
                            JarInputStream zis = new JarInputStream(new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(jarPojo.getJarPath())));
                            JarEntry ze = zis.getNextJarEntry();

                            boolean isFound = false;

                            while (ze != null) {

                                String baseName = ze.getName();
                                if (!ze.isDirectory() && baseName.equals(classPath)) {
                                    isFound = true;
                                    value = readInput(zis);
                                }

                                if (!isFound) {
                                    ze = zis.getNextJarEntry();
                                } else {
                                    ze = null;
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return value;
    }

    public void fetcherInit(JarInputStream zis) throws IOException {

        try {
            extractPackages(zis, currentJar.getName());
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void extractPackages(final JarInputStream zis, final String currentPath) throws IOException {
        if (zis != null) {
            JarEntry ze = zis.getNextJarEntry();

            while (ze != null) {
                if (!ze.isDirectory()) {

                    final String baseName = ze.getName();
                    final String fileExt = getFilenamePart(baseName, FilenamePart.FILE_EXT);

                    if (fileExt != null && !fileExt.isEmpty()) {
                        //Check the 4 first bytes
                        final byte[] currentBytes = new byte[4];
                        zis.read(currentBytes, 0, 4);

                        if (getFileType(currentBytes, fileExt).equals(FileTypeEnum.JAR)) {
                            executor.execute(new Runnable() {
                                byte[] start = currentBytes.clone();
                                byte[] end = readInput(zis);

                                public void run() {
                                    try {
                                        extractPackages(new JarInputStream(new BufferedInputStream(new ByteArrayInputStream(concatArrays(start, end)))), baseName);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } else if (getFileType(currentBytes, fileExt).equals(FileTypeEnum.CLASS)) {
                            String jarName = getFilename(currentPath, true);
                            String binaryName = getBinaryName(baseName);

                            if (cache.containsKey(jarName)) {
                                cache.get(jarName).addClass(binaryName, baseName);
                            } else {
                                JarPojo pojo = new JarPojo(jarName, currentPath, currentJar.getName().equals(jarName));
                                pojo.addClass(binaryName, baseName);

                                cache.put(jarName, pojo);
                            }
                        }
                    }
                }
                ze = zis.getNextJarEntry();
            }
        }
    }
}

class JarPojo {
    private String jarName, jarPath;
    private List<Pair<String, String>> classBinName;
    private boolean isRootJar;

    public JarPojo(String jarName, String jarPath, boolean isRootJar) {
        this.jarName = jarName;
        this.jarPath = jarPath;
        this.isRootJar = isRootJar;

        classBinName = new ArrayList<Pair<String, String>>();
    }

    public void addClass(String binaryName, String path) {
        if (binaryName != null && !binaryName.isEmpty() && path != null) {
            classBinName.add(new Pair<String, String>(binaryName, path));
        }
    }

    public boolean isRootJar() {
        return isRootJar;
    }

    public void setRootJar(boolean rootJar) {
        isRootJar = rootJar;
    }

    public String getJarName() {
        return jarName;
    }

    public void setJarName(String jarName) {
        this.jarName = jarName;
    }

    public String getJarPath() {
        return jarPath;
    }

    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    public List<Pair<String, String>> getClassBinName() {
        return classBinName;
    }

    public void setClassBinName(List<Pair<String, String>> classBinName) {
        this.classBinName = classBinName;
    }
}